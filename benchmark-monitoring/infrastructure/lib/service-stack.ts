import { App, Duration, RemovalPolicy, Stack } from 'aws-cdk-lib'
import {
  Tracing,
  Runtime,
  Code,
  Function as LambdaFunction,
} from 'aws-cdk-lib/aws-lambda'
import {
  IQueue,
  Queue,
  QueueEncryption,
} from 'aws-cdk-lib/aws-sqs'
import { SqsEventSource } from 'aws-cdk-lib/aws-lambda-event-sources'
import {
  ManagedPolicy,
  PolicyStatement,
  Role,
  ServicePrincipal,
} from 'aws-cdk-lib/aws-iam'
import { BenchmarkMonitoringStackProps } from './stack-configuration'

import { addMonitoring } from '../modules/monitoring'
import { addAlerting } from '../modules/alerting'
import { IKey, Key } from 'aws-cdk-lib/aws-kms'
import { ApiGatewayToSqs } from '@aws-solutions-constructs/aws-apigateway-sqs'
import { AttributeType, BillingMode, Table, TableEncryption } from 'aws-cdk-lib/aws-dynamodb'

interface DataIngestionLayer {
  sqsQueue: IQueue
  ingestionDeadLetterQueue: IQueue
}

export class ServiceStack extends Stack {
  constructor(scope: App, id: string, props: BenchmarkMonitoringStackProps) {
    super(scope, id, props)

    const kmsKey = this.createBenchmarkMonitoringKmsKey(props)
    const { sqsQueue, ingestionDeadLetterQueue } = this.createBenchmarkMonitoringDataIngestionLayer(kmsKey, props)
    const deadLetterQueue = this.createBenchmarkMonitoringLambdaDeadLetterQueue(props)
    const lambda = this.createBenchmarkMonitoringLambda(sqsQueue, kmsKey, deadLetterQueue, props)
    this.createBenchmarkMonitoringDynamoDb(lambda, kmsKey, props)
    addMonitoring(this, sqsQueue, lambda, deadLetterQueue, ingestionDeadLetterQueue, props)
    addAlerting(this, lambda, deadLetterQueue, ingestionDeadLetterQueue, props)
  }

  private createBenchmarkMonitoringKmsKey(props: BenchmarkMonitoringStackProps): Key {
    return new Key(this, 'BenchmarkMonitoringKmsKey', {
      description:
        'The KMS key used to encrypt the SQS queue used in Benchmark Monitoring',
      alias: `${props.appName}-sqs-encryption`,
      enableKeyRotation: true,
      enabled: true,
    })
  }

  private createBenchmarkMonitoringDataIngestionLayer(kmsKey: Key, props: BenchmarkMonitoringStackProps): DataIngestionLayer {
    const { sqsQueue, deadLetterQueue, apiGatewayRole } = new ApiGatewayToSqs(this, 'BenchmarkMonitoringDataIngestion', {
      queueProps: {
        queueName: props.appName,
        visibilityTimeout: Duration.seconds(props.eventsVisibilityTimeoutSeconds),
        encryption: QueueEncryption.KMS,
        dataKeyReuse: Duration.seconds(300),
        retentionPeriod: Duration.days(14),
      },
      deployDeadLetterQueue: true,
      maxReceiveCount: 10,
      allowCreateOperation: true,
      encryptionKey: kmsKey
    })
    if (!deadLetterQueue) {
      throw new Error('The ApiGatewayToSqs dependency did not yield a dead letter queue!')
    }
    kmsKey.grantEncryptDecrypt(apiGatewayRole);
    return { sqsQueue, ingestionDeadLetterQueue: deadLetterQueue.queue }
  }

  private createBenchmarkMonitoringLambdaDeadLetterQueue(props: BenchmarkMonitoringStackProps): IQueue {
    const sqsQueue = new Queue(
      this,
      'BenchmarkMonitoringLambdaDeadLetterQueue',
      {
        queueName: `${props.appName}-dlq`,
        encryption: QueueEncryption.KMS_MANAGED,
        dataKeyReuse: Duration.seconds(300),
        retentionPeriod: Duration.days(14)
      })

    return sqsQueue
  }

  private createBenchmarkMonitoringLambda(benchmarkMonitoringQueue: IQueue, benchmarkMonitoringKmsKey: IKey, deadLetterQueue: IQueue, props: BenchmarkMonitoringStackProps): LambdaFunction {
    const iamRole = new Role(
      this,
      'BenchmarkMonitoringLambdaIamRole',
      {
        assumedBy: new ServicePrincipal('lambda.amazonaws.com'),
        roleName: `${props.appName}-lambda-role`,
        description:
          'IAM Role for granting Lambda receive message to the Benchmark Monitoring queue and send message to the DLQ',
      }
    )

    iamRole.addToPolicy(
      new PolicyStatement({
        actions: ['kms:Decrypt', 'kms:GenerateDataKey'],
        resources: [benchmarkMonitoringKmsKey.keyArn],
      })
    )
    iamRole.addToPolicy(
      new PolicyStatement({
        actions: ['SQS:ReceiveMessage', 'SQS:SendMessage'],
        resources: [benchmarkMonitoringQueue.queueArn],
      })
    )
    iamRole.addToPolicy(
      new PolicyStatement({
        actions: ['SQS:SendMessage'],
        resources: [deadLetterQueue.queueArn],
      })
    )

    iamRole.addManagedPolicy(
      ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaBasicExecutionRole')
    )

    const lambda = new LambdaFunction(
      this,
      'BenchmarkMonitoringLambda',
      {
        description: 'This Lambda function ingests experimental results from infrastructure participating in experiments and stores collected data in a DynamoDB table',
        runtime: Runtime.NODEJS_18_X,
        code: Code.fromAsset('../lambda/build'),
        functionName: props.appName,
        handler: 'index.handler',
        timeout: Duration.seconds(props.functionTimeoutSeconds),
        memorySize: 512,
        tracing: Tracing.ACTIVE,
        role: iamRole,
        environment: {
          REGION: this.region,
          DEBUG: props.debug ? 'TRUE' : 'FALSE',
        },
        retryAttempts: 0
      }
    )

    lambda.addEventSource(
      new SqsEventSource(benchmarkMonitoringQueue,
        {
          batchSize: props.batchSize,
          maxBatchingWindow: props.maxBatchingWindow,
          reportBatchItemFailures: true
        })
    )

    return lambda
  }

  private createBenchmarkMonitoringDynamoDb(lambda: LambdaFunction, kmsKey: IKey, props: BenchmarkMonitoringStackProps) {
    const table = new Table(this, 'BenchmarkMonitoringDynamoDbTable', {
      tableName: props.appName,
      encryption: TableEncryption.CUSTOMER_MANAGED,
      encryptionKey: kmsKey,
      partitionKey: {
        name: 'experimentId',
        type: AttributeType.STRING,
      },
      readCapacity: 1,
      writeCapacity: 1,
      billingMode: BillingMode.PROVISIONED,
      removalPolicy: RemovalPolicy.DESTROY,
    })

    table.grantReadData(lambda)
    table.grantWriteData(lambda)
  }
}

export default {}

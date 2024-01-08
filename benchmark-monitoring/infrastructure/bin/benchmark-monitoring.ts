import { App, Duration } from 'aws-cdk-lib'
import { BenchmarkMonitoringStackProps } from '../lib/stack-configuration'
import { ServiceStack } from '../lib/service-stack'

const app = new App()

const stackProps: BenchmarkMonitoringStackProps = {
  description: 'Benchmark Monitoring',
  env: {
    account: '138945776678',
    region: 'eu-west-1'
  },
  appName: 'benchmark-monitoring',
  maxBatchingWindow: Duration.minutes(1),
  batchSize: 100,
  reservedConcurrentExecutions: 1,
  debug: true,
  functionTimeoutSeconds: 30,
  eventsVisibilityTimeoutSeconds: 30,
  alertingEnabled: true
}

new ServiceStack(app, 'benchmark-monitoring', stackProps)
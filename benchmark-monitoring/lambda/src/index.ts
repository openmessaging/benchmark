import { AttributeValue, BatchWriteItemCommand, DynamoDBClient, PutItemCommand } from '@aws-sdk/client-dynamodb'
import SqsBatchEvent from './model/sqsBatchEvent'
import { BatchItemFailures } from './model/batchItemFailure'
import SqsEventRecord from './model/sqsEventRecord'
import OperationResult from './model/operationResult'

const DYNAMO_DB_TABLE_NAME = 'benchmark-monitoring'
const REGION = 'eu-west-1'

interface Event {
  messageId: string
  experimentId: string
  // TODO: Add more data here.
}

const dynamoDbClient = new DynamoDBClient({ region: REGION })

const mapToEvent = (record: SqsEventRecord): OperationResult<Event | undefined> => {
  try {
    const event = JSON.parse(record.body)
    const data = { ...event, messageId: record.messageId }
    return OperationResult.successWithData(event.messageId, data)
  } catch (error: unknown) {
    console.error(`Error occurred while parsing record ${JSON.stringify(record)}`, error)
    return OperationResult.failure(record.messageId)
  }
}

const isValidEvent = (result: OperationResult<Event | undefined>): result is OperationResult<Event> =>
  !!result.data

const mapToDynamoDbEntry = (event: Event) =>
  Object.entries(event).reduce((acc, [key, value]) => {
    acc[key] = { S: value }
    return acc
  }, {} as Record<string, AttributeValue>)

const writeItemsToTableInBatch = async (events: Event[]): Promise<boolean> => {
  try {
    const input = {
      RequestItems: {
        [DYNAMO_DB_TABLE_NAME]: events
          .map(mapToDynamoDbEntry)
          .map(item => ({ PutRequest: { Item: item } })),
      },
    }
    const command = new BatchWriteItemCommand(input)
    await dynamoDbClient.send(command)
    return true
  } catch (error: unknown) {
    console.error(`Error occurred while writing batch ${JSON.stringify(events)}`, error)
    return false
  }
}

const writeItemsToTable = async (event: Event): Promise<OperationResult<undefined>> => {
  try {
    const input = {
      TableName: DYNAMO_DB_TABLE_NAME,
      Item: mapToDynamoDbEntry(event),
    }
    const command = new PutItemCommand(input)
    await dynamoDbClient.send(command)
    return OperationResult.success(event.messageId)
  } catch (error: unknown) {
    console.error(`Error occurred while writing event ${JSON.stringify(event)}`, error)
    return OperationResult.failure(event.messageId)
  }
}

const getLambdaOutput = (failedIds: string[]) => {
  console.log(JSON.stringify(failedIds))
  return new BatchItemFailures(failedIds)
}

export async function handler(batchEvent: SqsBatchEvent): Promise<BatchItemFailures> {
  try {
    console.log(`Detected event: ${JSON.stringify(batchEvent)}`)
    const transformedBatch = batchEvent.Records.map(mapToEvent)
    const invalidEventIds = transformedBatch
      .filter(e => !isValidEvent(e) || !e.successful)
      .map(e => e.itemIdentifier)
    const validEvents = transformedBatch
      .filter(isValidEvent)
      .filter(e => e.successful)
      .map(e => e.data)

    const wasBatchWriteSuccessful = await writeItemsToTableInBatch(validEvents)
    if (wasBatchWriteSuccessful) {
      return getLambdaOutput(invalidEventIds)
    }

    const promises = validEvents.map(writeItemsToTable)
    const results = await Promise.all(promises)

    const failedIds = results
      .filter(result => !result.successful)
      .map(result => result.itemIdentifier)
      .concat(invalidEventIds)
    return getLambdaOutput(failedIds)
  } catch (exception) {
    console.error(`Failed processing the event: ${JSON.stringify(batchEvent)}`, exception)
    throw exception
  }
}

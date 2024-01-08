import { Duration, StackProps } from 'aws-cdk-lib'

export interface BenchmarkMonitoringStackProps extends StackProps {
  appName: string
  maxBatchingWindow: Duration
  batchSize: number
  reservedConcurrentExecutions: number
  debug: boolean
  functionTimeoutSeconds: number
  eventsVisibilityTimeoutSeconds: number
  alertingEnabled: boolean
}

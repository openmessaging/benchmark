import SqsEventRecord from './sqsEventRecord'

interface SqsBatchEvent {
  Records: SqsEventRecord[]
}

export default SqsBatchEvent
class BatchItemFailure {
  readonly itemIdentifier: string

  constructor(itemIdentifier: string) {
    this.itemIdentifier = itemIdentifier
  }
}

export class BatchItemFailures {
  readonly batchItemFailures: BatchItemFailure[]
   
  constructor(failedItemIdentifiers: string[]) {
    this.batchItemFailures = failedItemIdentifiers.map(itemIdentifier => new BatchItemFailure(itemIdentifier))
  }
}
export default class OperationResult<T> {
  readonly itemIdentifier: string

  readonly successful: boolean

  readonly data: T

  private constructor(successful: boolean, itemIdentifier: string, data: T) {
    this.successful = successful
    this.itemIdentifier = itemIdentifier
    this.data = data
  }

  static successWithData<T>(itemIdentifier: string, data: T): OperationResult<T> {
    return new OperationResult(true, itemIdentifier, data)
  }

  static success(itemIdentifier: string): OperationResult<undefined> {
    return new OperationResult(true, itemIdentifier, undefined)
  }

  static failure(itemIdentifier: string): OperationResult<undefined> {
    return new OperationResult(false, itemIdentifier, undefined)
  }
}

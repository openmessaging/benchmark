// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function handler(event: any): Promise<any> {
  try {
    console.log(`Detected event: ${JSON.stringify(event)}`)
  } catch (exception) {
    console.error(`Failed processing the event: ${JSON.stringify(event)}`, exception)
    throw exception
  }
}

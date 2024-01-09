# Monitoring Lambda
The mission of this service is to collect experimental results from infrastructure participating in experiments in a push-based manner.

## Getting Started
The project can be set up for local development as follows.

### Requirements
* Node 18.x

### Install
Use Git to clone this repository to your computer.

```
git clone https://github.com/rtewierik/benchmark.git
```

Navigate to the root directory of the Lambda project.

``
cd benchmark/benchmark-monitoring
``

Install the project dependencies using NPM.

```
npm install
```

### Deploy

Ensure the environment variable `AWS_DEFAULT_PROFILE` is set if you want to use a specific profile (ex: `personal_prod`) over the default profile when deploying the CDK project to AWS.

Run the following commands locally to deploy the AWS CDK project to AWS.

* **Navigate to the `lambda` project and build it using npm:** `cd lambda && npm install && npm run build && cd ..`
* **Navigate to the `infrastructure` project, verify AWS environment and CDK version and build the CDK project:** `cd infrastructure && cdk doctor && npm install && npm run build`
* **Verify staged changes:** `npx aws-cdk diff benchmark-monitoring`
* **Deploy staged changes:** `npx aws-cdk deploy benchmark-monitoring --require-approval never`
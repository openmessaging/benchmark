## OMB Runner platform

An environment for running benchmarks and storing results.

### Playbook Definitions

1. **Deploy Runner** -
   One time setup of the EC2 control instance and s3 storage bucket.

2. **Deploy Testbed** -
   Deploys a cluster for testing a particular configuration.
   Includes updating OMB jar to the latest for the git repository.

3. **Run Benchmark** -
   Run one or more testcases including various workloads and driver configurations.

4. **Destroy Testbed** -
   Destroys a cluster once testing is completed.

5. **Get Benchmark Results** -
   Retrieves results by date and case.

### Playbook Details

1. deploy-runner
  - input:
    - omb: https://github.com/datastax/openmessaging-benchmark/

  - ec2
    - t3.small
    - s3 bucket
    
  - yum
    - git
    - wget
    - mvn
    - terraform
    - ansible
    - openjdk
    - openjdk-devel
    - vim
  - setup
    - create ssh key
    - create directory structures
    - clone ${omb}
  - scripts / playbooks
  	- deploy-testbed
  	- run-benchmark
  	- destroy-testbed
  	- get-results

  - output
    - status: success | failure

2. deploy-testbed
  - inputs
    - deploy: driver-pulsar/deploy
    - configuration: s4r.yaml <-- configured in repository
    - clients: #workers <--- update terraform.tfvars
    - brokers: #brokers/bookies <--- update terraform.tfvars

  - pull origin github.com/datastax/openmessaging-benchmark/
  - build openmessaging benchmark
  - create a deployment configuration
    - update terraform.tfvars
  - deploy omb configuration
    - export TF_STATE=.
    - terraform init
    - terraform apply
    - ansible playbook deploy w/ extra vars

  - output
    - status: success | failure

3. run-benchmark
  - inputs
    - date: Date or date-time
    - case: Name of test
    - args: bin/benchmark arguments

  - ssh to client machine
    - cd /opt/benchmark
    - sudo bin/benchmark ${args}
    - mkdir .../${date}/${case}
  - scp results to s3 bucket
    - json stats
    - install.yaml
    - log files
    
  - output
    - status: success | failure
    - latency:
      - end2end-50: ...
      - publish-50: ...
    - backlog: ...

4. destroy-testbed
  - inputs
    - deploy: driver-pulsar/deploy

  - destroy omb configuration
    - terraform destroy

  - output
    - status: success | failure

5. get-results
  - inputs
    - outdir: Place to save results
    - date: Date or date-time
    - case: Name of test

  - retrieve results for tests
    - bin/create-charts.py
    - scp results folder from s3 bucket
      - .../${date}/${case}
      - .../${date}

  - output
    - status: success | failure

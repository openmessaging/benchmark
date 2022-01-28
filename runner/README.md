## OMB Runner platform

An environment for running benchmarks and storing results.

### Playbook Definitions

1. **Deploy Runner** -
  - One time setup of the EC2 control instance and s3 storage bucket.

2. **OMB Package** -
  - Update OMB jar to the latest for the git repository.
  - Clients may be updated from time to time.
  - Allow for multiple OMB jars for deployments like for S4J.

3. **Deploy Testbed** -
  - Particular version of Pulsar cluster - builds a testbed.yaml.
  - Particular instance types - terraform.tfvars.
  - Deploys a cluster via:
    - terraform init
    - terraform apply
    - ansible playbook

4. **Run Benchmark** -
  - Run one or more testcases including various workloads and driver configurations.
    - ssh

5. **Destroy Testbed** -
  - Destroys a cluster once testing is completed.
    - terraform destroy

6. **Get Benchmark Results** -
  - Retrieves results by date and case.
    - scp

### Runner directory structure

- /opt/benchmark
  - a checkout of the OMB master branch
  - runner scripts, playbooks, and templates are in ./runner
- /opt/testbeds/${name}/${date}
  - setup by copying playbooks and templates from omb
  - the testbed deployment and templates
- /opt/results/${name}/${date}
  - the benchmark results

### Playbook Details

There should be a repository that includes these palybooks along with terraform state for the runner instance.

1. runner-deploy.yaml
   This playbook must block if the runner is already deployed.

  - ec2
    - t3.small
    
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
    - deploy scripts

  - output
    - status: success | failure

2. omb-package.yaml
   This playbook is used to compile the openmesssaging benchmark on the runner.
   This will create a singular package which may run special script like to include S4J.

  - inputs
    - gitrepos: https://github.com/datastax/openmessaging
    - post-script: <for making S4J adjustment, etc>
    - name: <name of OMB jar>

  - script
    - clone ${omb}
    - pull origin github.com/datastax/openmessaging-benchmark/
    - build openmessaging benchmark

  - output
    - status: success | failure

3. testbed-deploy.yaml
   This playbook will create a testbed with the provided configuration and the current OMB package.

  - inputs
    - candidate: pulsar release jar file
    - adaptors: one or more adaptor nar files
    - clients
      - instance-type
      - #workers: 4
    - brokers
      - instance-type
      - #brokers/bookies: 3

  - playbook
    - copy runner/deploy
    - copy omb package
    - update terraform.tfvars
    - export TF_STATE=.
    - terraform init
    - terraform apply
    - update cluster.yaml (ala s4r.yaml)
    - ansible playbook deploy w/ cluster.yaml

  - output
    - status: success | failure

4. benchmark.yaml
   This playbook will run benchmarks on a testbed.

  - inputs
    - date: Date or date-time
    - case: Name of test
    - args: bin/benchmark arguments
      - we will use specific drivers and workloads

  - ssh to client machine
    - cd /opt/benchmark
    - sudo bin/benchmark ${args}
    - mkdir .../${date}/${case}
  - scp results
    - json stats
    - install.yaml
    - log files
    
  - output
    - status: success | failure
    - latency:
      - end2end-50: ...
      - publish-50: ...
    - backlog: ...

5. results.yaml
   This playbook processes the results of benchmarks

  - inputs
    - outdir: Place to save results
    - date: Date or date-time
    - case: Name of test

  - retrieve results for tests
    - bin/create-charts.py
    - scp results folder
      - .../${date}/${case}
      - .../${date}

  - output
    - status: success | failure

6. testbed-destroy.yaml
   This playbook will destroy a testbed.

  - inputs
    - deploy: driver-pulsar/deploy

  - destroy omb configuration
    - terraform destroy

  - output
    - status: success | failure



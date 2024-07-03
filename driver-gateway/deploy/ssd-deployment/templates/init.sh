#!/bin/bash

echo "Installing Python 3 for Ansible"
yum install -y python3

echo "Installing Docker"
yum install -y yum-utils
yum-config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "Install other common tools"
yum install -y git jq wget java-17-openjdk sysstat vim chrony nmap yum-utils

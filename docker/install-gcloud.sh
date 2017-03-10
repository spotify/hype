#!/usr/bin/env bash
set -e

curl -s -o google-cloud-sdk.tar.gz https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-115.0.0-linux-x86_64.tar.gz
tar xzf google-cloud-sdk.tar.gz

printf 'y\ny\n' | ./google-cloud-sdk/install.sh
./google-cloud-sdk/bin/gcloud components install beta kubectl

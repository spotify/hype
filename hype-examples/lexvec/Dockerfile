FROM us.gcr.io/datawhere-test/hype-examples-base:4
MAINTAINER Romain Yon <romain@spotify.com>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"
ENTRYPOINT ["hype-run"]

# Install go
RUN apt-get update && apt-get install -y golang-go
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Download and compile word2vec, placing it in bin folder
RUN wget https://github.com/alexandres/lexvec/archive/master.zip
RUN unzip master.zip
RUN go build lexvec-master/lexvec.go lexvec-master/storage.go
RUN mv lexvec /bin/
RUN rm -rf /lexvec-master master.zip storage

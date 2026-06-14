# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2011-2021 ETH Zurich.

FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu AS build

WORKDIR /build

# z3 is installed from the Ubuntu repo — guaranteed native-arch binary.
# Boogie is built from source as a self-contained .NET binary for both amd64 and arm64,
# avoiding the OpenSSL 1.1 dependency of the older pre-built boogie-builder releases.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gnupg2 \
	curl \
	unzip \
	git \
	dotnet-sdk-8.0 \
	sysstat \
    z3 && \
    rm -rf /var/lib/apt/lists/*

RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && \
    apt-get update && \
    apt-get install sbt

ENV Z3_EXE=/usr/bin/z3

# Build Boogie as a self-contained .NET binary for the host architecture.
# Commit 7449a7a899c02c95 targets net6.0 and produces a single self-contained
# executable that needs neither Mono nor OpenSSL 1.1 at runtime.
RUN ARCH=$(uname -m) && \
    case "$ARCH" in \
      x86_64)  RID=linux-x64 ;; \
      aarch64) RID=linux-arm64 ;; \
      *) echo "Unsupported arch: $ARCH" && exit 1 ;; \
    esac && \
    git clone https://github.com/boogie-org/boogie.git /tmp/boogie && \
    cd /tmp/boogie && git checkout 7449a7a899c02c95 && \
    dotnet publish -c Release -r $RID --self-contained true \
        /p:PackAsTool=false /tmp/boogie/Source/Boogie.sln && \
    mkdir -p /boogie/binaries-linux && \
    cp -r /tmp/boogie/Source/BoogieDriver/bin/Release/net6.0/${RID}/publish/. \
        /boogie/binaries-linux/ && \
    mv /boogie/binaries-linux/BoogieDriver /boogie/binaries-linux/Boogie && \
    rm -rf /tmp/boogie

ENV BOOGIE_EXE=/boogie/binaries-linux/Boogie
# Required for .NET globalization on musl/minimal environments
ENV DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=true

# Setup Gobra
WORKDIR /build/gobra/
COPY build.sbt /build/gobra/
COPY genparser.sh /build/gobra/
COPY project /build/gobra/project
COPY conf /build/gobra/conf
COPY src /build/gobra/src
# Copies viperserver and, transitively, all of its dependencies (carbon, silicon and silver)
COPY viperserver /build/gobra/viperserver

ENV SBT_OPTS="-Xmx4g -Xss4m -Xms512m"
RUN sbt assembly


# a second build stage starts here that is based of a fresh image.
# note that only stuff explicitly copied from "build" will be present.
# in particular, `sbt`, the Gobra sources or the Viper dependencies are not part of this image
# environment variables are not carried over either and need to be redeclared.
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

# Boogie needs older OpenSSL ABI; z3 from Ubuntu repo is native-arch.
RUN apt-get update && \
    apt-get install -y --no-install-recommends libssl3 z3 && \
    find /usr/lib -name 'libssl.so.3' -exec sh -c 'ln -s "$1" "${1%3}1.1"' _ {} \; && \
    rm -rf /var/lib/apt/lists/*

# copy downloaded/built boogie — self-contained on both architectures, no dotnet runtime needed
COPY --from=build /boogie/binaries-linux /boogie
ENV BOOGIE_EXE=/boogie/Boogie

# To fix boogie exception
ENV DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=true

ENV Z3_EXE=/usr/bin/z3

WORKDIR /gobra
COPY --from=build /build/gobra/target/scala-2.13/gobra.jar .

# copy evaluation and tutorial examples
COPY src/test/resources/regressions/examples/evaluation ./evaluation
COPY src/test/resources/regressions/examples/tutorial-examples ./tutorial-examples

ENTRYPOINT ["java", "-Xss128m", "-jar", "gobra.jar"]

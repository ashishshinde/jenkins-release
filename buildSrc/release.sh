#!/usr/bin/env bash

usage() {
  cat <<EOF
usage: bash release.sh --module aerospike-jms-outbound --version 1.1.0 --release-notes-file release-notes.md --release-args
  -m  (Required)          Module to release
  -v  (Required)          Version
  -n  (Required)          Path of release notes files
  -a                      Additional arguments to pass to gradle release command
  -h                      Print usage help

Requires github credentials as environment variables GITHUB_USERNAME and GITHUB_TOKEN
EOF
}

while getopts m:v:n:a:h opt; do
  case "$opt" in
  m)
    module=${OPTARG}
    ;;
  v)
    version=${OPTARG}
    ;;
  n)
    releaseNotesFile=${OPTARG}
    ;;
  a)
    releaseArgs=${OPTARG}
    ;;
  h)
    usage
    exit 0
    ;;
  esac
done

if [ -z "$module" ]; then
  echo "Module name is required"
  exit
fi

if [ -z "$version" ]; then
  echo "Release version is required"
  exit
fi

if [ -z "$releaseNotesFile" ]; then
  echo "Release notes file is required"
  exit
fi

if [ -z "$GITHUB_USERNAME" ]; then
  echo "Github username environment variable GITHUB_USERNAME not set".
  exit
fi

if [ -z "$GITHUB_TOKEN" ]; then
  echo "Github access token environment variable GITHUB_TOKEN not set".
  exit
fi

echo "--------------------------------------------------------------------------"
echo "Releasing module:$module version:$version"
echo "Args release-notes-file:$releaseNotesFile release-args:$releaseArgs"
echo "--------------------------------------------------------------------------"

# Switch to module directory
#moduleDir=${module/aerospike-/}
#cd "$moduleDir" || exit

# Run the release task
./gradlew --no-daemon ":$module:release" ":$module:"publishGithubRelease" -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=$version -PreleaseNotesFile="$releaseNotesFile" $releaseArgs

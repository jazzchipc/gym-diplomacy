#!/bin/bash

ps -e -o pid,pcpu,command --sort=-pcpu | grep "parlance\|agents\|open-ai\|usr/bin/java"
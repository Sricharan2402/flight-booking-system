# User Prompts Log

## Prompt 1
starting with this prompt, log all prompts in a markdown file called userPrompts.md. Log them as they are.

## Prompt 2
is there a way for me to get my prompts log without doing this? Is there a custom command?

## Prompt 3
You are a senior engineer writing a design doc that is both HLD + LLD for a flight booking system. The implementation will be done in Kotlin + Spring Boot, Gradle, and local docker setup with Postgres, Redis, Kafka. I'll give a brief system context, you expand it into a professional but human-readable design doc. It should include intro, requirements, entities, components, flows, schema-level details, and the key algorithms (no need for actual code, just algo explanation). @designPrompt.md has the design prompt. Create a filed named DesignDoc.md with this.

## Prompt 4
check the entities again and update accordingly, also the HLD diagram is incorrect, client interacts with booking service as well, and the admin service interacts with DB to create flights, then pushes event to Kafka

## Prompt 5
Please analyze this codebase and create a CLAUDE.md file, which will be given to future instances of Claude Code to operate in this repository.

What to add:
1. Commands that will be commonly used, such as how to build, lint, and run tests. Include the necessary commands to develop in this codebase, such as how to run a single test.
2. High-level code architecture and structure so that future instances can be productive more quickly. Focus on the "big picture" architecture that requires reading multiple files to understand.

Usage notes:
- If there's already a CLAUDE.md, suggest improvements to it.
- When you make the initial CLAUDE.md, do not repeat yourself and do not include obvious instructions like "Provide helpful error messages to users", "Write unit tests for all new utilities", "Never include sensitive information (API keys, tokens) in code or commits".
- Avoid listing every component or file structure that can be easily discovered.
- Don't include generic development practices.
- If there are Cursor rules (in .cursor/rules/ or .cursorrules) or Copilot rules (in .github/copilot-instructions.md), make sure to include the important parts.
- If there is a README.md, make sure to include the important parts.
- Do not make up information such as "Common Development Tasks", "Tips for Development", "Support and Documentation" unless this is expressly included in other files that you read.
- Be sure to prefix the file with the following text:

```
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
```
## Prompt 6
the HLD diagram in @DesignDoc.md is still incorrect, there are two clients, User and Admin. Both interact via Gateway. Admin client to admin service, user client to search and booking service. Admin, Ingestion consumer, Booking and Search all interact with DB. Correct this

## Prompt 7
You missed admin client interacting with admin service. Stack them all vertically, admin and user clients. Followed by API Gateway, ALL services interact with the Database. ALL services except ingestion consumer gets requests from API Gateway. SERVICES REQUEST TO CACHES, not the other way around. SEPARATE the cache for booking and search service

## Prompt 8
separate out the flows in HLD in @DesignDoc.md. The client facing flows as one, the ingestion flow as the other

## Prompt 9
not the algo, just the HLD diagram

## Prompt 10
cehck the @userPrompts.md file. From the NEXT command (not this command), add similar logs of my user prompts

## Prompt 11
Convert this repository into a Kotlin Gradle Spring Boot project. Make changes as required - add build.gradle.kts changes, configure dependencies

## Prompt 12
lets keep it simple, continue with just the src folder, we dont need multi module setup. follow proper folder structure inside the src folder

## Prompt 13
lets not start coding ryt away. complete making this bootRUnnable with spring boot. Lets start actual coding later

## Prompt 14
do a gradle build

## Prompt 15
upgrade java to 21 and related configs. Also the dependencies that you've commented out, remove anything that is not immediately necessary, we can add them later on if required.

## Prompt 16
Going forward, log my prompts in the @userPrompts.md file in the same way its present. Just the prompt as it is

build-dev: update-environment
	docker compose build

build:
	docker build -t hmpps-prisoner-finance-sync-api .

update-dependencies:
	./gradlew useLatestVersions

analyse-dependencies:
	./gradlew dependencyCheckAnalyze --info

serve: build-dev
	docker compose up -d --wait

serve-environment:
	docker compose up --scale hmpps-prisoner-finance-sync-api=0 -d --wait

serve-clean-environment: stop-clean
	docker compose up --scale hmpps-prisoner-finance-sync-api=0 -d --wait

update-environment:
	docker compose pull

stop:
	docker compose down

stop-clean:
	docker compose down --remove-orphans --volumes

unit-test:
	./gradlew unitTest

integration-test:
	./gradlew integrationTest --warning-mode all

test: unit-test integration-test

e2e:
	./gradlew integrationTest --warning-mode all

lint:
	./gradlew ktlintCheck

format:
	./gradlew ktlintFormat

check:
	./gradlew check

update-structurizer-cli:
	docker pull structurizr/cli

export-theme: update-structurizer-cli
	docker run -it --rm -v ./docs/architecture/domain:/usr/local/structurizr structurizr/cli export -w theme.dsl -f theme
	mv ./docs/architecture/domain/theme-theme.json ./docs/architecture/domain/theme.json

update-structurizer-lite:
	docker pull structurizr/lite

WORKSPACE_PATH=./
WORKSPACE_FILE=workspace

serve-structurizer: update-structurizer-lite
	docker run -it --rm -p 8080:8080 -v .:/usr/local/structurizr -e STRUCTURIZR_WORKSPACE_PATH=$(WORKSPACE_PATH) -e STRUCTURIZR_WORKSPACE_FILENAME=$(WORKSPACE_FILE) structurizr/lite

serve-architecture: WORKSPACE_PATH=./docs/architecture
serve-architecture: WORKSPACE_FILE=workspace
serve-architecture: serve-structurizer

export-architecture: update-structurizer-cli
	rm -fr ./docs/architecture/mermaid
	rm -fr ./docs/architecture/plantuml
	rm -fr ./docs/architecture/png
	docker run -it --rm -v ./docs/architecture:/usr/local/structurizr structurizr/cli export -w ./workspace.dsl -f mermaid -o ./mermaid
	docker run -it --rm -v ./docs/architecture:/usr/local/structurizr structurizr/cli export -w ./workspace.dsl -f plantuml -o ./plantuml
	./bin/generate_images.sh

.PHONY: build-dev build update-dependencies analyse-dependencies serve serve-environment serve-clean-environment update-environment stop stop-clean unit-test integration-test test e2e lint format check serve-structurizer export-architecture

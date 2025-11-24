# HMPPS Prisoner Finance Architecture

## getting started

From the root of this repository run the following command to start the structurizr lite web service.

```shell
make serve-architecture
```

this will create a web service available at `http://localhost:8080` which will allow you to explore the workspace.

## Exporting diagrams

To export views to Mermaid, PlantUML and PNG run the following docker command from the root of the codebase;

```shell
make export-architecture
```

The mermaid code can be used directly in Github Markdown;

## Architecture

### System landscape

![Prisoner finance - system landscape](./png/structurizr-prisoner-finance-system-landscape.png)

### System contexts

![Prisoner finance - system context](./png/structurizr-prisoner-finance-system-context.png)

![NOMIS - system context](./png/structurizr-nomis-system-context.png)

### Container views

![Prisoner finance - container view](./png/structurizr-prisoner-finance-container-view.png)

### Component views

![Payment processing - component view](./png/structurizr-payments-component-view.png)

![General ledger - component view](./png/structurizr-general-ledger-component-view.png)

![NOMIS Sync - component view](./png/structurizr-sync-component-view.png)

### Deployment

![Prisoner finance - DEV deployment](./png/structurizr-prisoner-finance-deployment-dev.png)


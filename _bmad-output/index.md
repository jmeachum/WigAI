# WigAI Project Documentation Index

**WigAI** is a Bitwig Studio extension that enables AI agents to control music production through the Model Context Protocol (MCP).

**Quick Links:**
- 📊 [Project Overview](../docs/reference/project-overview.md) — Start here for executive summary
- 🎯 [Architecture Overview](planning-artifacts/architecture.md) — System design
- 📚 [API Reference](../docs/reference/api-reference.md) — Full MCP API spec
- 🏗️ [Component Deep Dive](../docs/reference/component-architecture-deep-dive.md) — Detailed component breakdown

---

## 📚 Documentation By Category

### 🎯 **Getting Started**
- [**Project Overview**](../docs/reference/project-overview.md) - Executive summary, architecture at a glance, tech stack
- [**Project Brief**](planning-artifacts/project-brief.md) - High-level project goals and scope
- [**Architecture Overview**](planning-artifacts/architecture.md) - Main system architecture and design patterns

### 🏗️ **Architecture & Design**
- [**Component Architecture - Deep Dive**](../docs/reference/component-architecture-deep-dive.md) - Detailed breakdown of every major component
- [**Component View**](../docs/reference/component-view.md) - Component structure and organization
- [**Sequence Diagrams**](../docs/reference/sequence-diagrams.md) - Key workflows and interactions
- [**Data Models**](../docs/reference/data-models.md) - Core data structures and entity models

### 🔌 **API & Integration**
- [**API Reference**](../docs/reference/api-reference.md) - Complete MCP API specification with all endpoints
- [**MCP Tools Reference**](../docs/mcp-tools-reference.md) - Detailed tool implementations and error codes
- [**Key References**](../docs/reference/key-references.md) - External documentation links and resources

### 🛠️ **Development**
- [**Tech Stack**](../docs/reference/tech-stack.md) - Technology choices and justification
- [**Project Structure**](../docs/reference/project-structure.md) - Directory layout and module organization
- [**Operational Guidelines**](../docs/reference/operational-guidelines.md) - Coding standards, testing, error handling
- [**Environment Variables**](../docs/reference/environment-vars.md) - Configuration and environment setup
- [**Semantic Versioning Guide**](../docs/reference/semantic-versioning-guide.md) - Version management strategy

### 🚀 **Deployment & Operations**
- [**Infrastructure & Deployment**](../docs/reference/infra-deployment.md) - Deployment procedures and infrastructure
- [**Testing Architecture**](../docs/reference/testing/mcp-endpoints-verification.md) - MCP endpoint verification tests

### 📋 **Requirements & Planning**
- [**Product Requirements**](planning-artifacts/prd.md) - Full PRD organized by epic
- [**Implementation Stories**](implementation-artifacts/) - Feature stories and implementation details

### 📊 **Workflow & Status**
- [**Workflow Status**](planning-artifacts/bmm-workflow-status.yaml) - BMM methodology progress tracking

---

## 🎯 **Quick Navigation by Use Case**

### I need to...

**...understand what this project does**
→ Start with [Project Overview](../docs/reference/project-overview.md), then read [Project Brief](planning-artifacts/project-brief.md)

**...understand the architecture**
→ Read [Architecture Overview](planning-artifacts/architecture.md), then dive into [Component Architecture Deep Dive](../docs/reference/component-architecture-deep-dive.md)

**...integrate with the MCP API**
→ Review [API Reference](../docs/reference/api-reference.md), then check [MCP Tools Reference](../docs/mcp-tools-reference.md) for tool details

**...add a new feature**
→ Find your story in [stories/](implementation-artifacts/), review [Architecture](planning-artifacts/architecture.md), check [Component Deep Dive](../docs/reference/component-architecture-deep-dive.md) for patterns

**...understand data flow**
→ Check [Sequence Diagrams](../docs/reference/sequence-diagrams.md) and [Data Models](../docs/reference/data-models.md)

**...set up development**
→ Read [Project Structure](../docs/reference/project-structure.md), [Environment Variables](../docs/reference/environment-vars.md), [Tech Stack](../docs/reference/tech-stack.md)

**...understand coding standards**
→ Review [Operational Guidelines](../docs/reference/operational-guidelines.md)

**...deploy the extension**
→ Check [Infrastructure & Deployment](../docs/reference/infra-deployment.md)

---

## 📊 **Project Metadata**

| Attribute | Value |
|-----------|-------|
| **Project Type** | Bitwig Studio Extension |
| **Language** | Java 21 (LTS) |
| **Build System** | Gradle (Kotlin DSL) |
| **Repository Type** | Monolith |
| **API Protocol** | MCP 0.11.0+ |
| **HTTP Server** | Jetty 11 (SSE/Streamable HTTP) |
| **Test Framework** | JUnit Jupiter 5 |
| **Target Platform** | Bitwig Studio 12+ |
| **Status** | Active Development |

---

## 🔍 **Document Organization**

### Architecture Docs (For Understanding System Design)
- For **system overview**: [Architecture Overview](planning-artifacts/architecture.md)
- For **component details**: [Component Architecture - Deep Dive](../docs/reference/component-architecture-deep-dive.md)
- For **interactions**: [Sequence Diagrams](../docs/reference/sequence-diagrams.md)
- For **data structures**: [Data Models](../docs/reference/data-models.md)

### API Docs (For Integration)
- For **endpoint specifications**: [API Reference](../docs/reference/api-reference.md)
- For **tool implementations**: [MCP Tools Reference](../docs/mcp-tools-reference.md)
- For **error handling**: [MCP Tools Reference - Error Codes](../docs/mcp-tools-reference.md#error-codes)

### Dev Docs (For Development)
- For **setup**: [Project Structure](../docs/reference/project-structure.md), [Environment Variables](../docs/reference/environment-vars.md)
- For **standards**: [Operational Guidelines](../docs/reference/operational-guidelines.md)
- For **tech choices**: [Tech Stack](../docs/reference/tech-stack.md)
- For **features**: [Implementation Stories](implementation-artifacts/)

### Requirements Docs (For Understanding Scope)
- For **product goals**: [Project Brief](planning-artifacts/project-brief.md)
- For **detailed requirements**: [Product Requirements Directory](planning-artifacts/archive/cycle-1-2025-12-15/)
- For **implementation tasks**: [Stories Directory](implementation-artifacts/archive/cycle-1-2025-12-15/)

---

## 📈 **Key Files by Category**

| Purpose | Files |
|---------|-------|
| **Architecture** | `planning-artifacts/architecture.md`, `../docs/reference/component-architecture-deep-dive.md`, `../docs/reference/component-view.md` |
| **API** | `../docs/reference/api-reference.md`, `../docs/mcp-tools-reference.md` |
| **Data** | `../docs/reference/data-models.md` |
| **Dev Setup** | `../docs/reference/project-structure.md`, `../docs/reference/environment-vars.md`, `../docs/reference/tech-stack.md` |
| **Operations** | `../docs/reference/operational-guidelines.md`, `../docs/reference/infra-deployment.md`, `../docs/reference/semantic-versioning-guide.md` |
| **Planning** | `planning-artifacts/archive/cycle-1-2025-12-15/`, `planning-artifacts/project-brief.md` |

---

## ✅ **For AI Agents**

When using these docs as context:

1. **Start with:** [Project Overview](../docs/reference/project-overview.md) for rapid context loading
2. **For architecture questions:** Check [Component Deep Dive](../docs/reference/component-architecture-deep-dive.md)
3. **For API questions:** Reference [API Reference](../docs/reference/api-reference.md) + [MCP Tools Reference](../docs/mcp-tools-reference.md)
4. **For data questions:** Use [Data Models](../docs/reference/data-models.md)
5. **For feature development:** Check related story in [archived stories](implementation-artifacts/archive/cycle-1-2025-12-15/)

---

**Last Updated:** 2025-12-13  
**Generated by:** BMM Document Project Workflow  
**Status:** Active Development (Phase 3 - Implementation)

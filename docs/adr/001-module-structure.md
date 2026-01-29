# ADR-001: Estructura Multi-Modulo

**Fecha:** 2026-01-17
**Estado:** Aceptado

## Contexto
Libreria Android con logica separada de UI.

## Decision
3 modulos: passkeyauth-core, passkeyauth-ui, sample

## Consecuencias
+ Consumidores eligen core solo o core+ui
+ Testing simplificado
- Mayor complejidad inicial
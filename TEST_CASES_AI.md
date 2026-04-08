# TEST_CASES_AI.md — Análisis de Casos de Prueba Generados con IA

**Feature**: `001-worker-health-check`
**Fecha**: 2026-04-08
**Propósito**: Documentar los casos de prueba generados por IA, los ajustes críticos realizados por el desarrollador, y la justificación técnica de cada corrección. Evidencia de análisis crítico en el ciclo HITL (Human-in-the-Loop).

---

## Metodología

Para las 3 HUs seleccionadas (US1, US3, US4), se generó una matriz inicial de casos de prueba con IA. Luego se auditó cada caso y se documentaron los ajustes. Mínimo 2 ajustes reales por HU con justificación técnica.

---

## HU-1 — Consultar estado global del worker (US1)

### Casos generados por IA (raw)

| ID IA | Descripción | Tipo | Cobertura |
|-------|-------------|------|-----------|
| IA-U1-01 | Verificar que el endpoint responde HTTP 200 | Happy path | Solo código HTTP |
| IA-U1-02 | Verificar que el body tiene campo `status` | Happy path | Solo presencia del campo |
| IA-U1-03 | Verificar que `status` puede ser UP | Happy path | Solo valor UP |

### Matriz completa después del ajuste humano

| ID | Descripción | Tipo | IA lo propuso | Ajuste | Justificación |
|----|-------------|------|---------------|--------|---------------|
| TC-U13 | `health_returnsUp_whenSnapshotIsUp` — traducción de estado UP al formato Actuator | Verificar | ✅ Sí (parcialmente) | Se convirtió en test de **traducción de puerto**, no solo de código HTTP | La IA testeaba el endpoint completo con Spring context; se refactorizó a test unitario del adapter sin levantar servidor |
| TC-U14 | `health_returnsWarn_whenSnapshotIsWarn` — `Health.status("WARN")` en lugar de `Health.up()` | Verificar | ❌ No | **Ajuste 1**: La IA no consideró el estado WARN porque Spring no lo tiene built-in | Spring Actuator no tiene `Health.warn()`. Hay que usar `Health.status(new Status("WARN"))`. La IA lo omitió completamente; detectado al revisar la documentación oficial de Spring |
| TC-U15 | `health_returnsDown_whenSnapshotIsDown` | Verificar | ✅ Sí | Nombre y aserción más precisos | — |
| TC-U16 | `health_includesReason_whenReasonIsNotNull` — detail "reason" condicional | Verificar | ❌ No | **Ajuste 2**: La IA no consideró que `reason` puede ser null y no debe incluirse en ese caso | Sin este test, el adapter incluiría `"reason": null` en el body — viola el contrato de respuesta definido en `contracts/health-endpoint.md` |
| TC-U17 | `health_includesAllMetricDetails_always` — 3 keys del contrato siempre presentes | Verificar | Parcialmente | Se agregó la aserción de los 3 keys específicos | La IA verificaba el Map no-vacío; no verificaba que `pendingEvents`, `failedEvents` y `lastProcessedAt` fueran siempre los keys presentes |

**Ajustes aplicados en US1**: 2 correcciones críticas
1. Estado WARN inexistente en la propuesta de IA (gap arquitectónico)
2. Campo `reason` condicional ignorado (gap de contrato)

---

## HU-3 — Alerta por eventos fallidos con umbrales (US3)

### Casos generados por IA (raw)

| ID IA | Descripción | Tipo | Problema |
|-------|-------------|------|---------|
| IA-U3-01 | Verificar WARN cuando failedEvents > 10 (mayor estricto) | Happy path | **Operador incorrecto**: usa `>` en lugar de `>=` |
| IA-U3-02 | Verificar DOWN cuando failedEvents > 20 | Happy path | **Mismo problema** |
| IA-U3-03 | Verificar UP cuando failedEvents = 0 | Happy path | Solo cubre el caso trivial |
| IA-U3-04 | Verificar misconfiguration | Happy path | No lo consideró |

### Matriz completa después del ajuste humano

| ID | Descripción | IA lo propuso | Ajuste | Justificación |
|----|-------------|---------------|--------|---------------|
| TC-U04 | `failedEvents == warnThreshold (10)` → WARN | Parcialmente (`> 10` incorrecto) | **Ajuste 1**: cambiar `> 10` a `== 10` (boundary exacto `>=`) | La IA usó `>` (mayor estricto). La spec dice `>=` (mayor o igual). Con `> 10`, el evento número 10 no dispara WARN — bug silencioso en producción. **Este es el boundary value más crítico** |
| TC-U05 | `failedEvents == downThreshold (20)` → DOWN | Parcialmente (`> 20` incorrecto) | **Ajuste 1** (mismo): boundary `== 20` debe ser DOWN | Igual que TC-U04 pero para DOWN. Sin este test el threshold 20 no cubría el valor exacto |
| TC-U06 | `failedEvents == 19` → WARN (no DOWN) | ❌ No | **Ajuste 2**: probar justo debajo del downThreshold | La IA no consideró la frontera entre WARN y DOWN. `failedEvents=19` debe ser WARN, no DOWN. Verificar que la transición WARN→DOWN es en exactamente 20 |
| TC-U07 | `failedEvents == 9` → UP | ❌ No | **Ajuste 2** (complemento): probar justo debajo del warnThreshold | Necesario para confirmar que el boundary inferior también es correcto (9 eventos = UP, no WARN) |
| TC-U08 | `downThreshold == warnThreshold` (misconfiguration) → DOWN gana | ❌ No | **Ajuste 3**: probar configuración inválida | La IA nunca consideró que un operador pueda configurar `warn=20, down=20`. La spec exige que el sistema maneje esto sin lanzar excepción |

**Ajustes aplicados en US3**: 3 correcciones críticas
1. Operador `>` vs `>=` en todos los boundaries (bug silencioso en producción)
2. Cobertura de la frontera WARN↔DOWN (valor 19) — la IA solo cubrió los extremos
3. Caso de misconfiguration — completamente ausente en la propuesta de IA

---

## HU-4 — Detectar scheduler detenido (US4)

### Casos generados por IA (raw)

| ID IA | Descripción | Tipo | Problema |
|-------|-------------|------|---------|
| IA-U4-01 | Verificar DOWN cuando `lastProcessedAt` es null | Happy path | **Error de diseño**: null debería ser UP informativo, no DOWN |
| IA-U4-02 | Verificar DOWN cuando scheduler lleva > 30s sin correr | Happy path | Correcto pero sin verificar el reason |
| IA-U4-03 | Verificar UP cuando scheduler corrió recientemente | Happy path | Solo cubre el caso trivial |

### Matriz completa después del ajuste humano

| ID | Descripción | IA lo propuso | Ajuste | Justificación |
|----|-------------|---------------|--------|---------------|
| TC-U09 | `lastProcessedAt = now() - 31s` → DOWN con reason "Scheduler stalled" | Parcialmente | Se agregó aserción del reason con segundos transcurridos | La IA verificaba solo el status; el contrato exige que el reason sea legible para el on-call ("stalled since X seconds") |
| TC-U10 | `lastProcessedAt = now() - 29s` → UP (no afectado por timeout) | ❌ No | **Ajuste 1**: probar justo debajo del timeout | La IA no probó el lado "sano" del boundary. Sin este test, una implementación que pone DOWN a cualquier `elapsed > 0` pasaría el test IA-U4-02 pero fallaría este |
| TC-U11 | `lastProcessedAt = Optional.empty()` → UP informativo (no DOWN) | IA propuso DOWN | **Ajuste 2 (crítico)**: invertir el resultado esperado de DOWN a UP | **La IA generó un test incorrecto**. Propuso que `null` → DOWN, pero la spec dice explícitamente que un fresh startup no debe generar DOWN. Este test sin corrección generaría falsos positivos en cada reinicio de Kubernetes |
| TC-U12 | Scheduler stalled (`elapsed > 30s`) + `failedEvents = 2` (< warnThreshold) → DOWN scheduler gana | ❌ No | **Ajuste 3**: probar prioridad de estados cuando coexisten dos condiciones | La IA nunca consideró la colisión de reglas. Si el scheduler está detenido (→ DOWN) pero hay pocos eventos fallidos (→ UP), ¿cuál gana? La spec dice DOWN siempre gana. Sin este test, una implementación "whichever is evaluated last" pasaría |

**Ajustes aplicados en US4**: 3 correcciones críticas
1. Boundary `< timeout` no cubierto (lado sano del threshold)
2. `null → DOWN` era incorrecto: debería ser UP informativo (la IA generó un test con resultado esperado equivocado)
3. Regla de prioridad de estados coexistentes: completamente ausente en la propuesta de IA

---

## Resumen de ajustes HITL por dimensión

| HU | Total casos IA | Casos con error | Casos nuevos agregados | Tipo de error más grave |
|----|---------------|-----------------|----------------------|------------------------|
| US1 | 3 | 2 (33% incorrectos) | 2 | WARN inexistente en diseño IA |
| US3 | 3 | 3 (100% con boundary incorrecto) | 2 | Operador `>` vs `>=` en todos los casos |
| US4 | 3 | 1 (33% con resultado esperado equivocado) | 3 | `null → DOWN` era el resultado esperado incorrecto |
| **Total** | **9** | **6 (67%)** | **7** | — |

> **Conclusión**: En 6 de los 9 casos iniciales generados por IA, el caso tenía un defecto —
> ya fuera un resultado esperado incorrecto, un operador de comparación erróneo, o
> un escenario crítico ausente. El análisis crítico humano fue indispensable para
> que la suite tuviera valor real como red de seguridad de reglas de negocio.

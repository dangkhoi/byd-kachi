package com.byd.clusternav.offcar

data class FakeTransportRecord(
    val ordinal: Int,
    val phase: String,
    val detail: String,
)

class FakeVehicleTransport {
    fun capture(plan: CommandPlan): List<FakeTransportRecord> {
        val records = mutableListOf<FakeTransportRecord>()
        fun add(phase: String, detail: String) {
            records += FakeTransportRecord(records.size + 1, phase, detail)
        }

        plan.candidate.preconditions.forEach { step ->
            add("READ", when (step) {
                is ReadStep.EvidenceCheck -> "EVIDENCE:${step.evidenceId}"
                is ReadStep.PropertyConfig -> "CONFIG:${step.target.label()}"
                is ReadStep.PriorValue -> "PRIOR:${step.target.label()}:${step.token}"
            })
        }
        plan.candidate.mutations.forEach { step ->
            when (step) {
                is MutationStep.CatalogMutation -> add(
                    "PROPOSE",
                    "${step.dimension}:${step.contract.target.label()}:${step.value.label()}",
                )
            }
        }
        plan.candidate.observations.forEach { step ->
            add("OBSERVE", when (step) {
                is Observation.ReadBack -> "READ_BACK:${step.target.label()}:${step.expected}"
                is Observation.Surface -> "${step.surface}:${step.state}:${step.expected}"
                is Observation.EvidenceOnly -> "EVIDENCE:${step.evidenceId}:${step.expected}"
            })
        }
        plan.candidate.inverse.forEach { step ->
            add("INVERSE", when (step) {
                is InverseStep.RestorePrior -> "RESTORE:${step.target.label()}:${step.token}"
                is InverseStep.VerifyRestored -> "VERIFY:${step.target.label()}"
            })
        }
        return records.toList()
    }

    private fun PropertySelector.label(): String = when (this) {
        is PropertySelector.Catalog -> "CATALOG:${property.name}"
        is PropertySelector.FreeForm -> "REJECTED_FREE_FORM"
        is PropertySelector.RawId -> "REJECTED_RAW_ID"
    }

    private fun MutationValue.label(): String = when (this) {
        is MutationValue.KnownInt -> "INT:$value"
        is MutationValue.KnownBoolean -> "BOOLEAN:$value"
        is MutationValue.KnownToken -> "TOKEN:${value.name}"
        is MutationValue.IntSequence -> "INT_SEQUENCE:${values.joinToString("|")}:RESTORE=$restorePrior"
        is MutationValue.KnownEnum -> "ENUM:$name:$encodedValue:EVIDENCE=$evidenceId"
        is MutationValue.GuessedEnum -> "REJECTED_GUESSED_ENUM"
        MutationValue.Unspecified -> "UNSPECIFIED_BLOCKED"
    }
}

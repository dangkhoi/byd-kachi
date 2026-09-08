package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PropertyCandidateValidatorTest {
    private fun base(): VehicleCandidate = CandidateScenarioGenerator().generate()
        .single { it.candidate.id == "OFFCAR-H4-AMAP-PROFILE" }.candidate

    private fun mutation(candidate: VehicleCandidate): MutationStep.CatalogMutation =
        candidate.mutations.single() as MutationStep.CatalogMutation

    private fun withMutation(
        candidate: VehicleCandidate,
        updated: MutationStep.CatalogMutation,
    ): VehicleCandidate = candidate.copy(mutations = listOf(updated))

    @Test
    fun `fully cited typed configured candidate passes validator`() {
        assertTrue(PropertyCandidateValidator.validate(base()).isEmpty())
    }

    @Test
    fun `artifact config access type provider and consumer proof are mandatory`() {
        val candidate = base()
        val original = mutation(candidate)
        val contract = original.contract.copy(
            access = PropertyAccess.READ,
            valueType = PropertyValueType.UNKNOWN,
            provider = ProviderProof(ProviderKind.UNKNOWN, verified = false),
            propertyConfigEvidenceId = null,
            propertyConfigVerified = false,
            expectedConsumerEvidenceId = null,
            artifactEvidenceIds = setOf("MISSING"),
        )
        val issues = PropertyCandidateValidator.validate(
            withMutation(candidate.copy(evidenceIds = listOf("MISSING")), original.copy(contract = contract)),
        )

        assertTrue(issues.containsAll(setOf(
            CandidateValidationIssue.MISSING_ARTIFACT_EVIDENCE,
            CandidateValidationIssue.MISSING_PROPERTY_CONFIG,
            CandidateValidationIssue.MISSING_WRITE_ACCESS,
            CandidateValidationIssue.MISSING_VALUE_TYPE,
            CandidateValidationIssue.MISSING_PROVIDER,
            CandidateValidationIssue.MISSING_EXPECTED_CONSUMER,
        )))
    }

    @Test
    fun `raw IDs free-form names mass and guessed enums are rejected`() {
        val candidate = base()
        val original = mutation(candidate)
        val raw = PropertySelector.RawId("raw")
        val rawIssues = PropertyCandidateValidator.validate(withMutation(
            candidate,
            original.copy(
                contract = original.contract.copy(target = raw),
                value = MutationValue.GuessedEnum("guess", 99),
                mode = MutationMode.MASS,
            ),
        ))
        val freeFormIssues = PropertyCandidateValidator.validate(withMutation(
            candidate,
            original.copy(contract = original.contract.copy(target = PropertySelector.FreeForm("name"))),
        ))

        assertTrue(rawIssues.containsAll(setOf(
            CandidateValidationIssue.RAW_ID,
            CandidateValidationIssue.MASS_MODE,
            CandidateValidationIssue.GUESSED_ENUM,
        )))
        assertTrue(CandidateValidationIssue.FREE_FORM_NAME in freeFormIssues)
    }

    @Test
    fun `numeric values require declared bounds and known enums require cited evidence`() {
        val candidate = base()
        val original = mutation(candidate)
        val outOfRange = original.copy(
            contract = original.contract.copy(boundedRange = 1..10),
            value = MutationValue.IntSequence(listOf(5, 11), restorePrior = true),
        )
        val missingRange = original.copy(value = MutationValue.KnownInt(2))
        val uncitedEnum = original.copy(value = MutationValue.KnownEnum("ON", 2, "H1"))

        assertTrue(CandidateValidationIssue.UNBOUNDED_VALUE in PropertyCandidateValidator.validate(withMutation(candidate, outOfRange)))
        assertTrue(CandidateValidationIssue.UNBOUNDED_VALUE in PropertyCandidateValidator.validate(withMutation(candidate, missingRange)))
        assertTrue(CandidateValidationIssue.MISSING_ARTIFACT_EVIDENCE in PropertyCandidateValidator.validate(withMutation(candidate, uncitedEnum)))
    }

    @Test
    fun `prior inverse and one-dimensional mutation are mandatory`() {
        val candidate = base()
        val original = mutation(candidate)
        val missingLifecycle = candidate.copy(preconditions = emptyList(), inverse = emptyList())
        val multiple = candidate.copy(mutations = listOf(
            original,
            original.copy(dimension = MutationDimension.NAV_HUD_GATE),
        ))

        assertTrue(PropertyCandidateValidator.validate(missingLifecycle).containsAll(setOf(
            CandidateValidationIssue.MISSING_PRIOR,
            CandidateValidationIssue.MISSING_INVERSE,
        )))
        assertTrue(CandidateValidationIssue.MULTI_DIMENSION_MUTATION in PropertyCandidateValidator.validate(multiple))
    }
}

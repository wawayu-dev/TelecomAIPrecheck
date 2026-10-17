package cn.telecom.precheck.precheck;

import cn.telecom.precheck.adapter.MockTicketContextProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ResultValidatorTest {
    final jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    final ResultValidator validator = new ResultValidator(JsonMapper.builder().findAndAddModules().build(),factory.getValidator());
    final EvidenceVerifier verifier=new EvidenceVerifier();
    final MockTicketContextProvider provider=new MockTicketContextProvider();
    @AfterEach void close() { factory.close(); }
    @Test void validOutputIsParsedAndUnknownAmountCanRemainNull() {
        var result=validator.parse(Samples.VALID.replace("30.00","null"),verifier.verify(provider.getContext("DEMO-001")));
        assertThat(result.disputedAmount()).isNull();
        assertThat(result.evidenceIds()).hasSize(3);
    }
    @Test void fabricatedEvidenceIsRejected() {
        assertThatThrownBy(() -> validator.parse(Samples.VALID.replace("BILL-001","FAKE"),verifier.verify(provider.getContext("DEMO-001"))))
                .hasMessage("INVALID_EVIDENCE_REFERENCE");
    }
    @Test void availableEvidenceRequiresAtLeastOneReference() {
        String output=Samples.VALID.replace("[\"BILL-001\",\"ORDER-001\",\"AUTH-001\"]","[]");
        assertThatThrownBy(() -> validator.parse(output,verifier.verify(provider.getContext("DEMO-001"))))
                .hasMessage("INVALID_EVIDENCE_REFERENCE");
    }
    @Test void duplicateReferencesAreRejected() {
        assertThatThrownBy(() -> validator.parse(Samples.VALID.replace("ORDER-001","BILL-001"),verifier.verify(provider.getContext("DEMO-001"))))
                .hasMessage("INVALID_EVIDENCE_REFERENCE");
    }
    @ParameterizedTest
    @ValueSource(strings={"unknownField","illegalAction","invalidEnum","negativeAmount","missingField","blankSummary","malformed","trailing","duplicateKey"})
    void rejectsInvalidStructureAndBusinessFields(String kind) {
        String invalid=switch(kind) {
            case "unknownField" -> Samples.VALID.replace("{","{\"refundAmount\":30,");
            case "illegalAction" -> Samples.VALID.replace("MANUAL_REVIEW","REFUND");
            case "invalidEnum" -> Samples.VALID.replace("VALUE_ADDED_CHARGE_DISPUTE","VIOLATION_CONFIRMED");
            case "negativeAmount" -> Samples.VALID.replace("30.00","-1");
            case "missingField" -> Samples.VALID.replace("\"currency\":\"CNY\",","");
            case "blankSummary" -> Samples.VALID.replace("客户对30元视频会员费提出异议，希望核实订购情况。","");
            case "malformed" -> "not json";
            case "trailing" -> Samples.VALID+" {}";
            default -> Samples.VALID.replace("{","{\"currency\":\"CNY\",");
        };
        assertThatThrownBy(() -> validator.parse(invalid,verifier.verify(provider.getContext("DEMO-001"))))
                .hasMessage("INVALID_OUTPUT");
    }
    @Test void missingAuthorizationCannotAdoptModelsViolationConclusion() {
        String output=Samples.forTicket("002").replace(",\"AUTH-002\"", "")
                .replace("客户对30元视频会员费提出异议，希望核实订购情况。","已确认违规扣费，应当退费");
        var result=validator.parse(output,verifier.verify(provider.getContext("DEMO-002")));
        assertThat(result.summary()).contains("不能据此认定违规扣费").doesNotContain("应当退费");
        assertThat(result.recommendation()).isEqualTo(PrecheckResult.Recommendation.VERIFY_AUTHORIZATION);
    }
}

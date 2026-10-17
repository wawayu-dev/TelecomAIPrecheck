package cn.telecom.precheck.review;
import cn.telecom.precheck.precheck.*;
import cn.telecom.precheck.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Service @Profile("mysql") @Validated
public class ReviewService {
    private final PrecheckMapper runs;
    private final ReviewMapper reviews;
    private final AuditMapper audits;
    public ReviewService(PrecheckMapper runs, ReviewMapper reviews, AuditMapper audits) {
        this.runs=runs; this.reviews=reviews; this.audits=audits;
    }
    @Transactional
    @PreAuthorize("hasRole('REVIEWER')")
    public Review submit(String id, @Valid ReviewRequest request, String trace) {
        String actor=CurrentActor.id();
        if (runs.find(id)==null) throw new PrecheckNotFoundException();
        // 条件 UPDATE 是并发裁决点；事务内提交人工结果和审计，不能只依赖前端禁用按钮。
        if (reviews.claim(id,request.expectedVersion())!=1) throw new ReviewConflictException();
        var safe=new ReviewRequest(request.expectedVersion(),request.decision(),mask(request.summary()),request.recommendation(),mask(request.reason()));
        reviews.insert(id,safe,actor,trace,request.expectedVersion()+1);
        if (audits.append(id,actor,"REVIEW_"+request.decision(),"PENDING_REVIEW","COMMITTED",trace)!=1)
            throw new IllegalStateException("Audit write failed");
        return reviews.find(id);
    }
    @PreAuthorize("hasAnyRole('OPERATOR','REVIEWER','AUDITOR')")
    public Review get(String id) {
        if(runs.find(id)==null) throw new PrecheckNotFoundException();
        return reviews.find(id);
    }
    @PreAuthorize("hasAnyRole('REVIEWER','AUDITOR')")
    public List<AuditEvent> audit(String id,long after,int limit) {
        if(runs.find(id)==null) throw new PrecheckNotFoundException();
        return audits.list(id,after,limit);
    }
    static String mask(String text) {
        return text.replaceAll("(?<![0-9])[0-9]{17}[0-9Xx](?![0-9])","[身份证已脱敏]")
            .replaceAll("(?<![0-9])1[3-9][0-9]{9}(?![0-9])","[手机号已脱敏]")
            .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[邮箱已脱敏]");
    }
}

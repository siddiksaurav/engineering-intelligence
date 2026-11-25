package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.ItemBlindCheck;
import com.dsinnovators.ei.review.ReasonMarks;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Takes the user's verdict on the AI's reason for an item of the blind check sample (F08). */
@RestController
@RequestMapping("/api/evidence-items/{id}/reason-mark")
class ReasonMarkController {

    private final ReasonMarks reasonMarks;

    ReasonMarkController(ReasonMarks reasonMarks) {
        this.reasonMarks = reasonMarks;
    }

    /** Stores the mark; a second mark of the same item supersedes the first. Answers with the stored mark. */
    @PutMapping
    ItemBlindCheck.Mark mark(@PathVariable long id, @RequestBody ReasonMarkRequest request) {
        request.requireComplete();
        return reasonMarks.mark(id, request.reasonRight(), request.problemMarks(), request.note());
    }
}

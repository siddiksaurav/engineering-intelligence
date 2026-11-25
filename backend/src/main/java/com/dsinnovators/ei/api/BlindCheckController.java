package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.BlindCheckSample;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The blind check of the AI's complexity judgment against the user's own labels (F08). */
@RestController
@RequestMapping("/api/blind-check")
class BlindCheckController {

    private final BlindCheckSample sample;

    BlindCheckController(BlindCheckSample sample) {
        this.sample = sample;
    }

    /** Picks the sample when none is stored; always answers with the stored sample. */
    @PostMapping("/sample")
    List<SampledItemResponse> pickSample() {
        return sample.pick().stream().map(SampledItemResponse::of).toList();
    }
}

package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.BlindCheckSample;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The blind check of the AI's complexity judgment against the user's own labels (F08). */
@RestController
@RequestMapping("/api/blind-check")
class BlindCheckController {

    private final BlindCheckSample sample;
    private final BlindCheckExport export;

    BlindCheckController(BlindCheckSample sample, BlindCheckExport export) {
        this.sample = sample;
        this.export = export;
    }

    /** Picks the sample when none is stored; always answers with the stored sample. */
    @PostMapping("/sample")
    List<SampledItemResponse> pickSample() {
        return sample.pick().stream().map(SampledItemResponse::of).toList();
    }

    /** The stored sample as the file to label: it holds no answer of the AI. */
    @GetMapping(value = "/sample.csv", produces = "text/csv;charset=UTF-8")
    String sampleFile() {
        return export.csv();
    }
}

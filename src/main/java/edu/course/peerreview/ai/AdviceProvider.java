package edu.course.peerreview.ai;

import edu.course.peerreview.service.PeerReviewService.AdviceInput;

/** 输出仅是参考文本，没有返回或保存人工分数的接口。 */
@FunctionalInterface
public interface AdviceProvider {
    String generate(AdviceInput input);
}

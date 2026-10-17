package edu.course.peerreview.attachment;

import edu.course.peerreview.service.BusinessException;
import java.util.*;

/** Snapshot guards replacement/removal against stale forms. null at legacy entry points means preserve. */
public record AttachmentChanges(Set<Long> expectedIds, Set<Long> removedIds, List<PreparedAttachment> additions) {
    public AttachmentChanges {
        expectedIds = Set.copyOf(expectedIds); removedIds = Set.copyOf(removedIds); additions = List.copyOf(additions);
        if (!expectedIds.containsAll(removedIds)) throw new BusinessException("不能移除不在原附件列表中的文件。");
    }
    public static AttachmentChanges add(PreparedAttachment attachment) { return new AttachmentChanges(Set.of(), Set.of(), List.of(attachment)); }
    public static AttachmentChanges empty() { return new AttachmentChanges(Set.of(), Set.of(), List.of()); }
}

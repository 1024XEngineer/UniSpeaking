package com.unispeaking.admin.quality;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;
import com.unispeaking.admin.quality.QualityIssueAdminService.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualityIssueAdminControllerTest {
    @Test
    void delegatesEveryEndpointIncludingEventExistenceCheck() {
        QualityIssueAdminService service = mock(QualityIssueAdminService.class);
        QualityIssueAdminController controller = new QualityIssueAdminController(service);
        UUID issueId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AdminAccount admin = new AdminAccount(adminId, "admin", "hash", AdminRole.TECHNICAL, true);
        QualitySummary summary = mock(QualitySummary.class);
        IssueListResponse list = mock(IssueListResponse.class);
        QualityIssueView view = mock(QualityIssueView.class);
        IssueEventsResponse events = mock(IssueEventsResponse.class);
        CreateIssueRequest create = mock(CreateIssueRequest.class);
        UpdateIssueRequest update = mock(UpdateIssueRequest.class);
        when(service.summary()).thenReturn(summary);
        when(service.list(IssueStatus.OPEN, IssuePlatform.MOBILE, IssueType.BUG, 10)).thenReturn(list);
        when(service.get(issueId)).thenReturn(view);
        when(service.events(issueId, 20)).thenReturn(events);
        when(service.create(create, adminId, "admin")).thenReturn(view);
        when(service.update(issueId, update, adminId, "admin")).thenReturn(view);

        assertSame(summary, controller.summary());
        assertSame(list, controller.issues(IssueStatus.OPEN, IssuePlatform.MOBILE, IssueType.BUG, 10));
        assertSame(view, controller.issue(issueId));
        assertSame(events, controller.events(issueId, 20));
        assertSame(view, controller.create(create, admin));
        assertSame(view, controller.update(issueId, update, admin));
        verify(service, org.mockito.Mockito.times(2)).get(issueId);
    }
}

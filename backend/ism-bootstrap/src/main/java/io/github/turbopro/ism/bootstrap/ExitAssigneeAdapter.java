package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.iam.access.UserReferenceService;
import io.github.turbopro.ism.supplier.ExitAssigneeVerifier;
import org.springframework.stereotype.Component;

@Component
public class ExitAssigneeAdapter implements ExitAssigneeVerifier {
    private final UserReferenceService users;
    public ExitAssigneeAdapter(UserReferenceService users){this.users=users;}
    @Override public boolean active(long userId){return users.activeForAssignment(userId);}
}

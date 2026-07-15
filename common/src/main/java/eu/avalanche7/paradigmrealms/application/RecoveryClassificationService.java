package eu.avalanche7.paradigmrealms.application;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;

public final class RecoveryClassificationService {
    public RecoveryAction classify(Realm realm) {
        return switch (realm.state()) {
            case ALLOCATED -> RecoveryAction.READY_TO_GENERATE;
            case GENERATING -> RecoveryAction.REPLAY_GENERATION;
            case FAILED -> RecoveryAction.ADMIN_REVIEW;
            case DELETING -> RecoveryAction.RESUME_DELETION;
            case READY, ACTIVE, ARCHIVED, DELETED -> RecoveryAction.NONE;
        };
    }
}

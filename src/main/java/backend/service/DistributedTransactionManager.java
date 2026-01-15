package backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Управление распределенной транзакцией с использованием двухфазного коммита
 */
public class DistributedTransactionManager {

    private static final Logger logger = Logger.getLogger(DistributedTransactionManager.class.getName());

    public static class TransactionParticipant {
        private final String name;
        private final Runnable prepare;
        private final Runnable commit;
        private final Runnable rollback;

        public TransactionParticipant(String name, Runnable prepare, Runnable commit, Runnable rollback) {
            this.name = name;
            this.prepare = prepare;
            this.commit = commit;
            this.rollback = rollback;
        }

        public String getName() {
            return name;
        }

        public void prepare() {
            prepare.run();
        }

        public void commit() {
            commit.run();
        }

        public void rollback() {
            rollback.run();
        }
    }

    /**
     * Выполняет распределенную транзакцию с использованием двухфазного коммита
     * @param participants участники транзакции
     * @param businessLogic основная бизнес-логика
     * @return результат выполнения бизнес-логики
     * @throws Exception в случае ошибки
     */
    public static <T> T execute(List<TransactionParticipant> participants, Supplier<T> businessLogic) throws Exception {
        List<TransactionParticipant> preparedParticipants = new ArrayList<>();
        T result = null;
        Exception businessException = null;

        // Фаза 1: Prepare
        try {
            logger.info("Phase 1: Prepare - starting for " + participants.size() + " participants");
            for (TransactionParticipant participant : participants) {
                try {
                    logger.info("Preparing participant: " + participant.getName());
                    participant.prepare();
                    preparedParticipants.add(participant);
                    logger.info("Participant prepared: " + participant.getName());
                } catch (Exception e) {
                    logger.severe("Failed to prepare participant " + participant.getName() + ": " + e.getMessage());
                    // Откатываем всех подготовленных участников
                    for (TransactionParticipant prepared : preparedParticipants) {
                        try {
                            prepared.rollback();
                        } catch (Exception rollbackEx) {
                            logger.severe("Failed to rollback " + prepared.getName() + ": " + rollbackEx.getMessage());
                        }
                    }
                    throw new RuntimeException("Transaction prepare failed for " + participant.getName(), e);
                }
            }
            logger.info("Phase 1: Prepare - completed successfully");

            // Выполняем бизнес-логику
            try {
                logger.info("Executing business logic");
                result = businessLogic.get();
                logger.info("Business logic completed successfully");
            } catch (Exception e) {
                businessException = e;
                logger.severe("Business logic failed: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            // Если произошла ошибка на этапе prepare или бизнес-логики, откатываем всех
            logger.severe("Transaction failed, rolling back all participants");
            for (TransactionParticipant participant : preparedParticipants) {
                try {
                    participant.rollback();
                } catch (Exception rollbackEx) {
                    logger.severe("Failed to rollback " + participant.getName() + ": " + rollbackEx.getMessage());
                }
            }
            if (businessException != null) {
                throw businessException;
            }
            throw e;
        }

        // Фаза 2: Commit (только если все успешно)
        try {
            logger.info("Phase 2: Commit - starting for " + preparedParticipants.size() + " participants");
            for (TransactionParticipant participant : preparedParticipants) {
                try {
                    logger.info("Committing participant: " + participant.getName());
                    participant.commit();
                    logger.info("Participant committed: " + participant.getName());
                } catch (Exception e) {
                    logger.severe("Failed to commit participant " + participant.getName() + ": " + e.getMessage());
                    // Пытаемся откатить уже закоммиченных участников
                    for (TransactionParticipant committed : preparedParticipants) {
                        if (committed != participant) {
                            try {
                                committed.rollback();
                            } catch (Exception rollbackEx) {
                                logger.severe("Failed to rollback " + committed.getName() + ": " + rollbackEx.getMessage());
                            }
                        }
                    }
                    throw new RuntimeException("Transaction commit failed for " + participant.getName(), e);
                }
            }
            logger.info("Phase 2: Commit - completed successfully");
        } catch (Exception e) {
            // Если commit не удался, пытаемся откатить всех
            logger.severe("Commit phase failed, attempting rollback");
            for (TransactionParticipant participant : preparedParticipants) {
                try {
                    participant.rollback();
                } catch (Exception rollbackEx) {
                    logger.severe("Failed to rollback " + participant.getName() + ": " + rollbackEx.getMessage());
                }
            }
            throw e;
        }

        return result;
    }
}


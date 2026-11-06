package ru.mirea.practice7.deliveries;

public final class DomainExceptions {
    private DomainExceptions() { }

    public static class NotFound extends RuntimeException {
        public NotFound(long id) { super("Доставка с id=" + id + " не найдена"); }
    }
    public static class InvalidData extends RuntimeException {
        public InvalidData(String reason) { super(reason); }
    }
    public static class InvalidTransition extends RuntimeException {
        public InvalidTransition(String from, String to) {
            super("Недопустимый переход: " + from + " -> " + to);
        }
    }
    public static class RemoteServiceUnavailable extends RuntimeException {
        public RemoteServiceUnavailable(String reason) { super(reason); }
    }
}

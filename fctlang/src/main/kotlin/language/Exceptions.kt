package language

class StuckException(reason: String) : RuntimeException(reason)

class InfiniteLoopException : RuntimeException()
package main.exceptions

class EmptyConditionException(name: String, cause: Throwable? = null) :
    DetailedException("Empty condition found in $name", cause)
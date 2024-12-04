package main

class EmptyConditionException(name: String, cause: Throwable? = null) :
    DetailedException("Empty condition found in $name", cause)
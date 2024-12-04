package main

class StringNotFoundException(missingString: String, stringList: List<String?>, cause: Throwable? = null) :
    DetailedException("String '$missingString' not found in the list: $stringList", cause)
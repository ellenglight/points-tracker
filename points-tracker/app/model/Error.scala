package model

object Error {

  class LowBalanceException(msg: String) extends Exception(msg)
  class UserDoesNotExistException(msg: String) extends Exception(msg)

}

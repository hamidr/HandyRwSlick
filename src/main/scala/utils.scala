package object Utils {

  object RwDummies {
    case class Dummy1()
    case class Dummy2()
    case class Dummy3()
    case class Dummy4()
    case class Dummy5()
    case class Dummy6()

    implicit def dummy1 = Dummy1()
    implicit def dummy2 = Dummy2()
    implicit def dummy3 = Dummy3()
    implicit def dummy4 = Dummy4()
    implicit def dummy5 = Dummy5()
    implicit def dummy6 = Dummy6()
  }
}

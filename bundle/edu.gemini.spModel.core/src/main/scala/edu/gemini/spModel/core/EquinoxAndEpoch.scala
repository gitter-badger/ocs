package edu.gemini.spModel.core

import scalaz.Equal


// Equinox (reference system) and Epoch (time of observation) are distinct but related.
// See this article for more http://en.wikipedia.org/wiki/Epoch_(astronomy)

sealed trait Equinox extends Product with Serializable {

  /** Return a function to convert coordinates to the specified equinox. */
  def toEquinox(target: Equinox): Coordinates => Coordinates =
    ???

}

sealed abstract class EquinoxWithEpoch(val epoch: Epoch) extends Equinox

object Equinox {
  case object J2000 extends EquinoxWithEpoch(Epoch.J2000)
  case object B1950 extends EquinoxWithEpoch(Epoch.B1950)
  case object Apparent extends Equinox
}

sealed abstract class Epoch {
  def value: Double
  def toUnixTime: Long = ???
}

object Epoch {

  case class JulianYears(value: Double) extends Epoch
  case class BesselianYears(value: Double) extends Epoch

  val J2000 = JulianYears(2000.0)
  val B1950 = JulianYears(1950.0)

}




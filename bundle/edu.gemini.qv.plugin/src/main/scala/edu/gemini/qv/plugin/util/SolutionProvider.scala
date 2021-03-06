package edu.gemini.qv.plugin.util

import edu.gemini.qpt.shared.sp.Obs
import edu.gemini.qv.plugin.QvContext
import edu.gemini.qv.plugin.ui.QvGui
import edu.gemini.qv.plugin.util.ConstraintsCache._
import edu.gemini.qv.plugin.util.ScheduleCache.ScheduleEvent
import edu.gemini.qv.plugin.util.SolutionProvider.{ConstraintType, ValueType}
import edu.gemini.skycalc.TimeUtils
import edu.gemini.spModel.core.{Peer, Site}
import edu.gemini.util.skycalc.Night
import edu.gemini.util.skycalc.calc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.swing.Publisher
import scala.util.{Failure, Success}

object SolutionProvider {

  trait ConstraintType
  trait ValueType

  private val providers: Map[Site, SolutionProvider] = Map(
    Site.GN -> new SolutionProvider(Site.GN),
    Site.GS -> new SolutionProvider(Site.GS)
  )

  def apply(site: Site) = providers(site)
  def apply(peer: Peer) = providers(peer.site)
  def apply(ctx: QvContext) = providers(ctx.site)
}


sealed class SolutionProvider(site: Site) extends Publisher {

  // ====================================================================
  // the default start of our range is today and it goes on for a year
  val range: Interval = {
    val start = TimeUtils.startOfDay(System.currentTimeMillis(), site.timezone)
    val end = TimeUtils.endOfDay(start + TimeUtils.days(364), site.timezone)
    // update semester data accordingly
    SemesterData.update(site, Interval(start, end))
    Interval(start, end)
  }

  val nights = SemesterData.nights(site, range)
  // ====================================================================

  val scheduleCache = new ScheduleCache()
  val constraintsCache = new ConstraintsCache(nights)

  deafTo(this) // avoid cycles
  listenTo(scheduleCache, constraintsCache)
  reactions += {
    case e: CalculationEvent => publish(e) // forward
    case e: ScheduleEvent => publish(e) // forward
  }

  def clear() = {
    scheduleCache.clear()
    constraintsCache.clear()
  }

  /** Reloads and recalculates all constraints in the background. */
  def update(ctx: QvContext, newObservations: Set[Obs], oldObservations: Set[Obs]): Future[Unit] = {
    // check if horizons IDs are all known, show a warning if not
    checkHorizonsNames(newObservations)

    // do the actual update
    scheduleCache.update(ctx.peer, range)
    constraintsCache.update(ctx.peer, nights, newObservations)
  }

  def values(nights: Seq[Night], valueType: ValueType, obs: Obs): Seq[Double] =
    constraintsCache.value(nights, valueType, obs)

  def value(valueType: ValueType, night: Night, obs: Obs): Double =
    constraintsCache.value(Seq(night), valueType, obs).head

  def remainingHours(ctx: QvContext, o: Obs): Long = {
    val n = SemesterData.current(ctx.site).nights.find(n => n.end > System.currentTimeMillis()).get
    val s = solution(Seq(n), Set[ConstraintType](Elevation), o).restrictTo(n.interval)
    val set = s.intervals.find(_.end < n.nauticalTwilightEnd).map(_.end).getOrElse(n.nauticalTwilightEnd)
    set - n.nauticalTwilightStart
  }

  def remainingNights(ctx: QvContext, obs: Obs, thisSemester: Boolean, nextSemester: Boolean): Int = {
    val now = System.currentTimeMillis()
    val nights = remainingNights(ctx.site, thisSemester, nextSemester)
    if (nights.size > 0) {
      solution(nights, ctx.selectedConstraints, obs).intervals.
        filter(i => i.end > now).
        map(i => TimeUtils.startOfDay(i.start, ctx.site.timezone())).
        toSet.size
    } else 0
  }

  def remainingTime(ctx: QvContext, obs: Obs, thisSemester: Boolean, nextSemester: Boolean): Long = {
    val now = System.currentTimeMillis()
    val nights = remainingNights(ctx.site, thisSemester, nextSemester)
    if (nights.size > 0) {
      solution(nights, ctx.selectedConstraints, obs).intervals.
        filter(i => i.end > now).
        map(_.duration).
        sum
    } else 0
  }

  private def remainingNights(site: Site, thisSemester: Boolean, nextSemester: Boolean): Seq[Night] = {
    val now = System.currentTimeMillis()
    val ts = if (thisSemester) SemesterData.current(site).nights.filter(n => n.sunrise > now) else Seq()
    val ns = if (nextSemester) SemesterData.next(site).nights else Seq()
    ts ++ ns
  }

  def telescopeSchedule = scheduleCache.schedule
  def telescopeScheduleUrl = scheduleCache.scheduleUrl

  def solution(nights: Seq[Night], constraints: Set[ConstraintType], observations: Set[Obs]): Solution = {
    val solutions: Set[Solution] = observations.map(o => solution(nights, constraints, o))
    // reduce all solutions to a single solution by combining all of them
    if (solutions.size > 0) solutions.reduce(_ combine _) else Solution()
  }

  // calculate the solution for one single observation applying all constraints
  // if needed minimal time constraint is applied, making sure that observation is available for at least
  // the specified amount of time between nautical twilight bounds
  def solution(nights: Seq[Night], constraints: Set[ConstraintType], observation: Obs): Solution = {

    // combine all cached solutions
    val cachedConstraints = constraints - MinimumTime  // MinTime is not cached!
    val solution = Seq(
      constraintsCache.solution(nights, cachedConstraints, observation),
      scheduleCache.solution(nights, constraints, observation)
    ).reduce(_ intersect _)

    // if needed apply minimum time constraint for every single night
    if (constraints.contains(MinimumTime)) {
      val minTime = minDurationFor(nights.head, observation)
      Solution(solution.intervals.filter(i => i.duration >= minTime))
    }
    else solution

  }

  // Note: See note for minElevationFor()
  private def minDurationFor(n: Night, o: Obs) =
    if (o.getLGS && n.site == Site.GS) TimeUtils.minutes(60)  // minimal science time for GeMS (LGS + site = GS): 60 minutes
    else if (o.getLGS) TimeUtils.minutes(30)                  // minimal science time for Altair + LGS: 30 minutes
    else TimeUtils.minutes(30)                                // minimal science time for everything else: 30 minutes


  private def checkHorizonsNames(observations: Set[Obs]): Unit = {
    val withoutHorizonsName =
      observations.
        filter(o => o.isNonSidereal && NonSiderealCache.horizonsNameFor(o).isEmpty).
        map(_.getObsId)
    if (withoutHorizonsName.size > 0) {
      val os = withoutHorizonsName.mkString(", ")
      val (plural, have, be) = if (withoutHorizonsName.size > 1) ("s", "have", "are") else ("", "has", "is")
      QvGui.showWarning(
        "Missing Horizons Lookup Name" + plural,
        s"""Non-sidereal observation$plural $os $have no name for Horizons lookups defined yet.
           |Please use the OT to do a first Horizons lookup to set the name$plural.
           |For now the single already defined position$plural $be used as the target position$plural.""".stripMargin
      )
    }
  }

}




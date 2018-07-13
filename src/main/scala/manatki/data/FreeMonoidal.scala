package manatki.data
import cats.arrow.FunctionK
import cats.{Applicative, Eval, ~>}
import cats.syntax.functor._
import cats.syntax.apply._
import manatki.data

sealed trait FreeMonoidal[F[_], A] {
  def mapK[G[_]](fk: F ~> G): FreeMonoidal[G, A] = data.FreeMonoidal.Trans[F, G](fk)(this)
  def fold(implicit F: Applicative[F]): F[A]
  def foldMap[G[_]: Applicative](fk: F ~> G): G[A] = mapK(fk).fold
}

object FreeMonoidal {
  type FM[F[_], A]  = FreeMonoidal[F, A]
  type DFM[F[_], A] = Day[F, FM[F, ?], A]
  def pure[F[_], A](a: A): FreeMonoidal[F, A] = Pure[F, A](a)

  def lift[F[_], A](fa: F[A]): FreeMonoidal[F, A] =
    Cons(liftDay(fa))

  private def liftDay[F[_], A](fa: F[A]): DFM[F, A] =
    Day(fa, pure[F, Unit](()))((a, _) => a)

  private[FreeMonoidal] case class Trans[F[_], G[_]](f: F ~> G) extends FunctionK[FM[F, ?], FM[G, ?]] {
    override def apply[A](fa: FM[F, A]): FM[G, A] = fa match {
      case Pure(a)   => Pure(a)
      case Cons(day) => Cons(Day(f(day.fx), apply(day.gy))(day.comb))
    }

  }

  final case class Pure[F[_], A](a: A) extends FreeMonoidal[F, A]{
    override def fold(implicit F: Applicative[F]): F[A] = F.pure(a)
}
  final case class Cons[F[_], A](day: Day[F, FreeMonoidal[F, ?], A]) extends FreeMonoidal[F, A]{
    override def fold(implicit F: Applicative[F]): F[A] = F.map2(day.fx, day.gy.fold)(day.comb)
}

  implicit def instance[F[_]]: Applicative[FreeMonoidal[F, ?]] =
    new FreeMonoidalApplicative

  class FreeMonoidalApplicative[F[_]] extends Applicative[FreeMonoidal[F, ?]] {
    override def pure[A](x: A): FreeMonoidal[F, A] = Pure(x)

    override def ap[A, B](ff: FreeMonoidal[F, A => B])(fa: FreeMonoidal[F, A]): FreeMonoidal[F, B] = map2(ff, fa)(_(_))

    override def map2[A, B, Z](fa: FreeMonoidal[F, A], fb: FreeMonoidal[F, B])(f: (A, B) => Z): FreeMonoidal[F, Z] =
      map2Eval(fa, Eval.now(fb))(f).value

    override def map2Eval[A, B, Z](fa: FM[F, A], fb: Eval[FM[F, B]])(f: (A, B) => Z): Eval[FM[F, Z]] =
      fa match {
        case Pure(a) => fb.map(_.map(b => f(a, b)))
        case Cons(day) =>
          map2Eval(day.gy, fb)((_, _)).map(tail => Cons(Day(day.fx, tail) { case (x, (y, b)) => f(day.comb(x, y), b) }))
      }
  }

}

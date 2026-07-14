#pragma once

#include "general.hpp"

// Once an equation has been calculated and saved, we (usually) don't do any more math
// with it. In this case, it is more efficient on memory and during iteration to convert
// the normal LinComMap to a vector.
template <template <typename> class Trig>
using EqVec = LinComVecZ<Trig<math::LinComArr<XY, Coeff16>>>;

class Evaluator {
  private:
    mpz_t quot;
    mpz_t rem;

    mpq_t xq;
    mpq_t yq;
    mpq_t argq; // In theory we can remove this variable, but it is very helpful for clarity

    mpfr_t term;
    mpfr_t sum;

    mpfr_t half_pi_d;
    mpfr_t half_pi_u;

  public:
    explicit Evaluator(const uint32_t prec);

    ~Evaluator();

    // Cover recursion calls Evaluator-backed sign checks for many adjacent
    // squares. Reusing one evaluator per worker thread preserves MPFR/MPFI
    // scratch-state isolation while avoiding repeated heap allocation of the
    // same temporaries inside hot cover paths.
    static Evaluator& thread_local_instance(const uint32_t prec);

    template <template <typename> class Trig>
    bool is_positive(const Equation<Trig>& eq, const Coeff64 bx, const Coeff64 by,
                     const PointQ& center, const Rational& rx, const Rational& ry);

    template <template <typename> class Trig>
    bool is_positive(const EqVec<Trig>& eq, const Coeff64 bound,
                     const PointQ& center, const Rational& radius);
};

extern template bool Evaluator::is_positive(const Equation<Sin>& eq, const Coeff64 bx, const Coeff64 by,
                                            const PointQ& center, const Rational& rx, const Rational& ry);
extern template bool Evaluator::is_positive(const Equation<Cos>& eq, const Coeff64 bx, const Coeff64 by,
                                            const PointQ& center, const Rational& rx, const Rational& ry);

extern template bool Evaluator::is_positive(const EqVec<Sin>& eq, const Coeff64 bound,
                                            const PointQ& center, const Rational& radius);
extern template bool Evaluator::is_positive(const EqVec<Cos>& eq, const Coeff64 bound,
                                            const PointQ& center, const Rational& radius);

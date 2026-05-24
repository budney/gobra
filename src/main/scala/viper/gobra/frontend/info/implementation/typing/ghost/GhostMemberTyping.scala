// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.frontend.info.implementation.typing.ghost

import org.bitbucket.inkytonik.kiama.util.Messaging.{Messages, error, noMessages}
import viper.gobra.ast.frontend.{PAccess, PAssume, PBlock, PCodeRootWithResult, PDeref, PDot, PExplicitGhostMember, PFPredicateDecl, PFunctionDecl, PFunctionSpec, PGhostMember, PGhostStatement, PIdnUse, PImplementationProof, PInhale, PInvoke, PMember, PMPredicateDecl, PMethodDecl, PMethodImplementationProof, PMethodReceivePointer, PNode, POld, PParameter, PPredicateAccess, PPreserves, PReturn, PVariadicType, PWithBody}
import viper.gobra.ast.frontend.{AstPattern => ap}
import viper.gobra.frontend.info.base.SymbolTable.{Function => FuncSymbol, MPredicateSpec, MethodImpl, MethodSpec}
import viper.gobra.frontend.info.base.Type.{InterfaceT, Type, UnknownType}
import viper.gobra.frontend.info.implementation.TypeInfoImpl
import viper.gobra.frontend.info.implementation.typing.BaseTyping
import viper.gobra.util.Violation

trait GhostMemberTyping extends BaseTyping { this: TypeInfoImpl =>

  private[typing] def wellDefGhostMember(member: PGhostMember): Messages = member match {
    case PExplicitGhostMember(_) => noMessages

    case PFPredicateDecl(_, args, body) =>
      body.fold(noMessages)(assignableToSpec) ++ nonVariadicArguments(args)

    case PMPredicateDecl(_, receiver, args, body) =>
      body.fold(noMessages)(assignableToSpec) ++
        isReceiverType.errors(miscType(receiver))(member) ++
        nonVariadicArguments(args)
  }

  private[typing] def wellFoundedIfNeeded(member: PMember): Messages = {
    val spec = member match {
      case m: PMethodDecl => m.spec
      case f: PFunctionDecl => f.spec
      case _ => Violation.violation("Unexpected member type")
    }
    val hasMeasureIfNeeded =
      if (spec.isPure || isEnclosingGhost(member))
        config.disableCheckTerminationPureFns || spec.terminationMeasures.nonEmpty
      else
        true
    val needsMeasureError =
      error(member, "All pure or ghost functions and methods must have termination measures, but none was found for this member.", !hasMeasureIfNeeded)
    needsMeasureError
  }

  private[typing] def noConditionalMeasureIfGhostOrPure(member: PMember): Messages = {
    val spec = member match {
      case m: PMethodDecl => m.spec
      case f: PFunctionDecl => f.spec
      case _ => return noMessages
    }
    if (spec.isPure || isEnclosingGhost(member))
      noConditionalMeasureErrors(spec.terminationMeasures)
    else noMessages
  }

  private def pureFunctionsDoNotNeedMayInitMsg = "Pure functions and methods cannot open package invariants," +
    "and thus, they must not be annotated with 'mayInit'."

  private[typing] def wellDefIfPureMethod(member: PMethodDecl): Messages = {
    if (member.spec.isPure) {
      isSingleResultArg(member) ++
        isSinglePureReturnExpr(member) ++
        isPurePostcondition(member.spec) ++
        pureMembersCannotHavePreserves(member.spec) ++
        nonVariadicArguments(member.args) ++
        error(member, pureFunctionsDoNotNeedMayInitMsg, member.spec.mayBeUsedInInit)
    } else noMessages
  }

  // Preserves clauses are unnecessary in pure functions. Any condition that holds on entry is guaranteed to
  // hold on exit, thus it is redundant to make properties both pre- and postconditions.
  private[typing] def pureMembersCannotHavePreserves(member: PFunctionSpec): Messages = {
    assert(member.isPure)
    member.clauses.collect{ case PPreserves(exp) => exp } flatMap { c =>
      error(c, "Pure functions and pure methods cannot have preserves clauses." +
        "Considering replacing this preserves clause with a precondition.")
    }
  }

  private[typing] def wellDefIfPureMethodImplementationProof(implProof: PMethodImplementationProof): Messages = {
    if (implProof.isPure) {
      isSinglePureReturnExpr(implProof) // all other checks are taken care of by super implementation
    } else noMessages
  }

  private[typing] def wellDefIfPureFunction(member: PFunctionDecl): Messages = {
    if (member.spec.isPure) {
      isSingleResultArg(member) ++
        isSinglePureReturnExpr(member) ++
        isPurePostcondition(member.spec) ++
        pureMembersCannotHavePreserves(member.spec) ++
        nonVariadicArguments(member.args) ++
        error(member, pureFunctionsDoNotNeedMayInitMsg, member.spec.mayBeUsedInInit)
    } else noMessages
  }

  private[typing] def wellDefIfVerifiedFunction(member: PFunctionDecl): Messages = {
    if (member.spec.isVerified) {
      val check = validateVerifiedSpecClause("function", member.id.name, _: PNode, _: String)
      error(member, s"\"verified\" function \"${member.id.name}\" must have a body. The postcondition is promoted to a domain axiom that must be earned by verifying the body; a bodyless declaration would produce an unproven axiom.",
            member.body.isEmpty) ++
      error(member, s"function \"${member.id.name}\" annotated with \"verified\" must have a decreases clause",
            member.spec.terminationMeasures.isEmpty) ++
      error(member, s"\"verified\" function \"${member.id.name}\" must have exactly one return value. The postcondition is promoted to a Viper domain axiom whose conclusion must be an expression over the return value; void functions produce a dead axiom and are not meaningful as verified.",
            member.result.outs.size != 1) ++
      member.spec.pres.flatMap(check(_, "precondition")) ++
      member.spec.posts.flatMap(check(_, "postcondition")) ++
      member.spec.preserves.flatMap(check(_, "preserves clause")) ++
      member.body.toVector.flatMap { case (_, block) =>
        allChildren(block).collect {
          case a: PAssume => error(a, s"function \"${member.id.name}\" annotated with \"verified\" must not contain assume statements. Axioms from verified functions must be earned, not assumed.")
          case a: PInhale => error(a, s"function \"${member.id.name}\" annotated with \"verified\" must not contain inhale statements. Axioms from verified functions must be earned, not assumed.")
        }.flatten
      }
    } else noMessages
  }

  private[typing] def wellDefIfVerifiedMethod(member: PMethodDecl): Messages = {
    if (member.spec.isVerified) {
      val isPtr = member.receiver.typ.isInstanceOf[PMethodReceivePointer]
      val check = validateVerifiedSpecClause("method", member.id.name, _: PNode, _: String, isPtr)
      error(member, s"\"verified\" method \"${member.id.name}\" must have a body. The postcondition is promoted to a domain axiom that must be earned by verifying the body; a bodyless declaration would produce an unproven axiom.",
            member.body.isEmpty) ++
      error(member, s"method \"${member.id.name}\" annotated with \"verified\" must have a decreases clause",
            member.spec.terminationMeasures.isEmpty) ++
      error(member, s"\"verified\" method \"${member.id.name}\" must have exactly one return value. The postcondition is promoted to a Viper domain axiom whose conclusion must be an expression over the return value; void methods produce a dead axiom and are not meaningful as verified.",
            member.result.outs.size != 1) ++
      member.spec.pres.flatMap(check(_, "precondition")) ++
      member.spec.posts.flatMap(check(_, "postcondition")) ++
      member.spec.preserves.flatMap(check(_, "preserves clause")) ++
      member.body.toVector.flatMap { case (_, block) =>
        allChildren(block).collect {
          case a: PAssume => error(a, s"method \"${member.id.name}\" annotated with \"verified\" must not contain assume statements. Axioms from verified functions must be earned, not assumed.")
          case a: PInhale => error(a, s"method \"${member.id.name}\" annotated with \"verified\" must not contain inhale statements. Axioms from verified functions must be earned, not assumed.")
        }.flatten
      }
    } else noMessages
  }

  /** Check one spec clause for constructs incompatible with verified domain axioms.
    * clauseKind is "precondition", "postcondition", or "preserves clause".
    * checkFieldRead should be true for pointer-receiver methods. */
  private def validateVerifiedSpecClause(
      memberKind: String, memberName: String,
      p: PNode, clauseKind: String, checkFieldRead: Boolean = false
  ): Messages = {
    (if ((p +: allChildren(p)).exists(_.isInstanceOf[POld]))
      error(p, s"\"verified\" $memberKind \"$memberName\" uses old(...) in its $clauseKind. State-snapshotting for verified axioms is not yet supported.")
    else noMessages) ++
    (if (containsHeapPermission(p))
      error(p, s"\"verified\" $memberKind \"$memberName\" has a heap-permission assertion (acc(...)) in its $clauseKind. Viper domain axioms cannot contain resource assertions.")
    else noMessages) ++
    (if (containsPointerDereference(p))
      error(p, s"\"verified\" $memberKind \"$memberName\" dereferences a pointer in its $clauseKind. Viper domain axioms cannot contain heap accesses; consider passing the dereferenced value as a plain parameter instead.")
    else noMessages) ++
    (if (checkFieldRead && (p +: allChildren(p)).exists {
      case dot: PDot => resolve(dot) match {
        case Some(ap.FieldSelection(_, _, _, _)) => true
        case _                                   => false
      }
      case _ => false
    })
      error(p, s"\"verified\" $memberKind \"$memberName\" has a pointer receiver and its $clauseKind reads a field through the heap. Viper domain axioms cannot contain location accesses. Consider using a value receiver or removing field reads from the $clauseKind.")
    else noMessages)
  }

  /** True if the expression tree rooted at `p` contains any heap-permission assertion
    * (`acc(...)` or `acc(pred(...))`).  Such assertions are illegal inside Viper domain
    * axioms, so any `verified` function/method whose spec contains them would either
    * crash the backend or produce a silently unsound axiom. */
  private def containsHeapPermission(p: PNode): Boolean =
    p.isInstanceOf[PAccess] || p.isInstanceOf[PPredicateAccess] ||
      allChildren(p).exists(n => n.isInstanceOf[PAccess] || n.isInstanceOf[PPredicateAccess])

  /** True if the expression tree rooted at `p` contains any pointer dereference (`*ptr`).
    * Pointer dereferences require heap state to evaluate and are therefore illegal inside
    * Viper domain axioms for the same reason as acc() assertions. */
  private def containsPointerDereference(p: PNode): Boolean =
    p.isInstanceOf[PDeref] ||
      allChildren(p).exists(_.isInstanceOf[PDeref])

  private def isSingleResultArg(member: PCodeRootWithResult): Messages = {
    error(member, "For now, pure methods and pure functions must have exactly one result argument", member.result.outs.size != 1)
  }

  private def isSinglePureReturnExpr(member: PWithBody): Messages = {
    member.body match {
      case Some((_, b: PBlock)) => isPureBlock(b)
      case None => noMessages
      case Some(b) => error(member, s"For now, the body of a pure method or pure function is expected to be a single return with a pure expression, got $b instead")
    }
  }

  private[typing] def isPureBlock(block: PBlock): Messages = {
    block.nonEmptyStmts match {
      case Vector(PReturn(Vector(ret))) => isPureExpr(ret)
      case b => error(block, s"For now, the body of a pure block is expected to be a single return with a pure expression, got $b instead")
    }
  }

  private def isPurePostcondition(spec: PFunctionSpec): Messages = spec.posts flatMap isPureExpr

  private[typing] def nonVariadicArguments(args: Vector[PParameter]): Messages = args.flatMap {
    p: PParameter => error(p, s"Pure members cannot have variadic arguments, but got $p", p.typ.isInstanceOf[PVariadicType])
  }

  /** Check that no verified function or method is self-recursive or mutually recursive (v1 restriction). */
  def wellVerifiedRecursionCheck: Messages = {
    val verifiedFuncs: Vector[PFunctionDecl] =
      tree.root.programs.flatMap(_.declarations.collect { case f: PFunctionDecl if f.spec.isVerified => f })
    val verifiedMethods: Vector[PMethodDecl] =
      tree.root.programs.flatMap(_.declarations.collect { case m: PMethodDecl if m.spec.isVerified => m })
    val allVerified: Vector[PNode] = verifiedFuncs ++ verifiedMethods

    if (allVerified.isEmpty) return noMessages

    val verifiedFuncDecls: Set[PFunctionDecl] = verifiedFuncs.toSet
    val verifiedMethodDecls: Set[PMethodDecl]  = verifiedMethods.toSet

    // Display name used in error messages
    def nodeName(n: PNode): String = n match {
      case f: PFunctionDecl => s"func ${f.id.name}"
      case m: PMethodDecl   => s"method ${m.id.name}"
      case _                => n.toString
    }

    // Resolve a vector of AST nodes to the set of verified member declarations they call.
    def resolveVerifiedCallees(nodes: Vector[PNode]): Set[PNode] =
      nodes.collect {
        case invoke: PInvoke => resolve(invoke) match {
          case Some(ap.FunctionCall(ap.Function(_, symb: FuncSymbol), _))
              if symb.isVerified && verifiedFuncDecls.contains(symb.decl) =>
            Some(symb.decl: PNode)
          case Some(ap.FunctionCall(ap.ReceivedMethod(_, _, _, symb: MethodImpl), _))
              if symb.isVerified && verifiedMethodDecls.contains(symb.decl) =>
            Some(symb.decl: PNode)
          case Some(ap.FunctionCall(ap.MethodExpr(_, _, _, symb: MethodImpl), _))
              if symb.isVerified && verifiedMethodDecls.contains(symb.decl) =>
            Some(symb.decl: PNode)
          case _ => None
        }
      }.flatten.toSet

    // Collect edges from verified member body calls to other verified members.
    def calleesInBody(n: PNode): Set[PNode] = {
      val bodyNodes: Vector[PNode] = n match {
        case f: PFunctionDecl => f.body.toVector.flatMap { case (_, block) => allChildren(block) }
        case m: PMethodDecl   => m.body.toVector.flatMap { case (_, block) => allChildren(block) }
        case _                => Vector.empty
      }
      resolveVerifiedCallees(bodyNodes)
    }

    // Collect edges from verified member spec (pre/post/preserves) calls to other verified members.
    // Spec-level mutual references create circular domain axioms and must be rejected.
    // We include each spec expression itself (not just its children) in case a bare PInvoke
    // is used directly as a spec expression (e.g. `requires g(x)` where g returns bool).
    def calleesInSpec(n: PNode): Set[PNode] = {
      val specNodes: Vector[PNode] = n match {
        case f: PFunctionDecl =>
          (f.spec.pres ++ f.spec.posts ++ f.spec.preserves).flatMap(p => p +: allChildren(p))
        case m: PMethodDecl =>
          (m.spec.pres ++ m.spec.posts ++ m.spec.preserves).flatMap(p => p +: allChildren(p))
        case _ => Vector.empty
      }
      resolveVerifiedCallees(specNodes)
    }

    val callGraph: Map[PNode, Set[PNode]] =
      allVerified.map(n => n -> (calleesInBody(n) ++ calleesInSpec(n))).toMap

    // Tarjan's SCC algorithm over PNode keys (reference identity via `eq` for stack termination)
    var index = 0
    val indexMap = scala.collection.mutable.Map[PNode, Int]()
    val lowlink  = scala.collection.mutable.Map[PNode, Int]()
    val onStack  = scala.collection.mutable.Set[PNode]()
    val stack    = scala.collection.mutable.ArrayBuffer[PNode]()
    var msgs: Messages = noMessages

    def strongconnect(v: PNode): Unit = {
      indexMap(v) = index
      lowlink(v)  = index
      index += 1
      stack.append(v)
      onStack.add(v)

      for (w <- callGraph.getOrElse(v, Set.empty)) {
        if (!indexMap.contains(w)) {
          strongconnect(w)
          lowlink(v) = math.min(lowlink(v), lowlink(w))
        } else if (onStack.contains(w)) {
          lowlink(v) = math.min(lowlink(v), indexMap(w))
        }
      }

      if (lowlink(v) == indexMap(v)) {
        val scc = scala.collection.mutable.ArrayBuffer[PNode]()
        var continue = true
        while (continue) {
          val w = stack.remove(stack.size - 1)
          onStack.remove(w)
          scc.append(w)
          if (w eq v) continue = false
        }
        if (scc.size > 1 || (scc.size == 1 && callGraph.getOrElse(v, Set.empty).contains(v))) {
          val cycle = scc.toVector
          val decl  = cycle.head
          if (cycle.size == 1)
            msgs ++= error(decl, s"\"verified\" ${nodeName(decl)} calls itself. Recursive domain axioms are unsound.")
          else {
            // Tarjan pops SCC members in reverse discovery order; reversing restores call order
            // so the cycle reads A -> B -> C -> A rather than C -> B -> A -> C.
            val ordered = cycle.reverse
            msgs ++= error(decl, s"\"verified\" members form a recursion cycle: ${ordered.map(nodeName).mkString(" -> ")} -> ${nodeName(ordered.head)}. Recursive domain axioms are unsound.")
          }
        }
      }
    }

    for (n <- allVerified if !indexMap.contains(n)) {
      strongconnect(n)
    }
    msgs
  }

  override lazy val localImplementationProofs: Vector[(Type, InterfaceT, Vector[String], Vector[String])] = {
    val implementationProofs = tree.root.programs.flatMap(_.declarations.collect{ case m: PImplementationProof => m})
    implementationProofs.flatMap { ip =>
      val z = (symbType(ip.subT), underlyingType(symbType(ip.superT)))
      z match {
        case (UnknownType, _) => None
        case (subT, superT: InterfaceT) =>
          Some((subT, superT, ip.alias.map(_.left.name), ip.memberProofs.map(_.id.name)))
        case _ => None
      }
    }
  }

  /**
    * Depends on which packages are loaded. Only call at the end of type checking.
    * Either returns a set of errors caused by invalid or missing implementation proofs
    * or a set of implementation proofs that have to be generated.
    **/
  def wellImplementationProofs: Either[Messages, Vector[(Type, InterfaceT, MethodImpl, MethodSpec)]] = {
    // the main context reports missing implementation proof for all packages (i.e. all packages that have been parsed & typechecked so far)
    if (isMainContext) {
      // we not only collect the type information for directly imported packages but for all transitively imported ones:
      val typeInfos = getTransitiveTypeInfos()
      val allRequiredImplements = {
        val foundRequired = typeInfos.flatMap(_.localRequiredImplements)
        val foundGuaranteed = typeInfos.flatMap(_.localGuaranteedImplements)
        foundRequired diff foundGuaranteed
      }
      if (allRequiredImplements.nonEmpty) {
        // For every required implementation, check that there is at most one proof
        // and if not all predicates are defined, then check that there is a proof.

        val providedImplProofs = typeInfos.flatMap(_.localImplementationProofs)
        val groupedProofs = allRequiredImplements.toVector.map{ case (impl, itf) =>
          (impl, itf, providedImplProofs.collect{ case (`impl`, `itf`, alias, proofs) => (alias, proofs) })
        }
        val multiples = groupedProofs.collect{ case (impl, itf, ls) if ls.size > 1 => (impl, itf) }

        lazy val groupedProofs2 = groupedProofs.foldLeft(Map.empty[(MethodImpl, MethodSpec), Vector[(Type, InterfaceT)]]){
          case (res, (impl, itf, aliasAndProofs)) =>
            if (aliasAndProofs.nonEmpty) {
              val x = aliasAndProofs.head._2.flatMap{ methodName => (getMember(impl, methodName), getMember(itf, methodName)) match {
                case (Some((implSymb: MethodImpl, _)), Some((itfSymb: MethodSpec, _))) => Some((implSymb, itfSymb) -> (impl, itf))
                case _ => None
              }}.toMap
              (res.keySet ++ x.keySet).map(k => k -> (res.getOrElse(k, Vector.empty) ++ x.get(k))).toMap
            } else res
        }
        // if there is more than one proof for the same pair of implementation and spec (can happen with embedded interfaces)
        lazy val multiples2 = groupedProofs2.toVector.collect{ case (key, values) if values.size > 1 => (key, values) }

        val msgs = if (multiples.nonEmpty) {
          error(multiples.head._2.decl, s"There is more than one proof for type ${multiples.head._1} implementing an interface")
        } else if (multiples2.nonEmpty) {
          val firstNode = multiples2.head._1._1.context.getTypeInfo.regular(multiples2.head._1._1.decl.id).rep
          error(firstNode, s"There is more than one proof for ${multiples2.map(x => x._1._1.decl.id.name).mkString(", ")}.")
        } else {
          // check that all predicates are defined
          groupedProofs.flatMap { case (impl, itf, ls) =>
            if (ls.nonEmpty) noMessages //
            else {
              val superPredNames = memberSet(itf).collect { case (n, m: MPredicateSpec) => (n, m) }
              val allPredicatesDefined = PropertyResult.bigAnd(superPredNames.map { case (name, symb) =>
                val valid = tryMethodLikeLookup(impl, PIdnUse(name)).isDefined
                failedProp({
                  val argTypes = symb.args map symb.context.typ

                  s"predicate $name is not defined for type $impl. " +
                    s"Either declare a predicate 'pred ($impl) $name(${argTypes.mkString(", ")})' " +
                    s"or declare a predicate 'pred p($impl${if (argTypes.isEmpty) "" else ", "}${argTypes.mkString(", ")})' with some name p and add 'pred $name := p' to the implementation proof."
                }, !valid)
              })
              allPredicatesDefined.asReason(tree.root,
                s"The code requires that $impl implements the interface $itf. An implementation proof cannot be inferred because predicate definitions are missing."
              )
            }
          }
        }
        if (msgs.nonEmpty) Left(msgs)
        else {

          val requiredImplMethAndSuperMeth = allRequiredImplements.flatMap { case (impl, itf) =>
            val superMethNames = memberSet(itf).collect { case (n, m: MethodSpec) => (n, m) }
            superMethNames.flatMap{ case (name, itfSymb) => getMember(impl, name) match {
              case Some((implSymb: MethodImpl, _)) => Some((implSymb, itfSymb))
              case _ => None
            }}
          }

          // syntactically detect errors that prevent one method from implementing a spec
          val implErrors = requiredImplMethAndSuperMeth.toVector flatMap {
            case (mImpl, mSpec) => methodImplMightImplementSpec(mImpl, mSpec)
          }
          if (implErrors.nonEmpty) {
            Left(implErrors)
          } else {
            // compute missing implementation proofs
            val missingImplMethAndSuperMeth = requiredImplMethAndSuperMeth
              .filter { case (implSymb, itfSymb) => !groupedProofs2.contains((implSymb, itfSymb)) }

            Right(missingImplMethAndSuperMeth.toVector.map { case (implSymb, itfSymb) =>
              val impl = implSymb.context.symbType(implSymb.decl.receiver.typ)
              val itf = itfSymb.itfType
              (impl, itf, implSymb, itfSymb)
            })
          }
        }
      } else Left(noMessages)
    } else Left(noMessages)
  }

  // Syntactically determine if a method implementation mImpl cannot possibly implement a specification mSpec.
  // This is useful to provide feedback quickly, before we verify the program.
  private def methodImplMightImplementSpec(mImpl: MethodImpl, mSpec: MethodSpec): Messages = {
    if (mSpec.spec.spec.terminationMeasures.nonEmpty && mImpl.decl.spec.terminationMeasures.isEmpty)
      error(mImpl.decl.spec, s"This method tries to implement a terminating interface method, " +
        s"but it does not provide a termination measure.")
    else
      noMessages
  }
}

// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.frontend.info.implementation.typing.ghost

import org.bitbucket.inkytonik.kiama.util.Messaging.{Messages, error, noMessages}
import viper.gobra.ast.frontend.{PAccess, PAssume, PBlock, PCodeRootWithResult, PDeref, PDot, PExplicitGhostMember, PFPredicateDecl, PFunctionDecl, PFunctionSpec, PGhostMember, PGhostStatement, PIdnUse, PImplementationProof, PInhale, PInvoke, PMember, PMPredicateDecl, PMethodDecl, PMethodImplementationProof, PMethodReceivePointer, PMethodSig, PNode, POld, PParameter, PPredicateAccess, PPreserves, PReturn, PVariadicType, PWithBody}
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
      error(member, s"\"verified\" function \"${member.id.name}\" must have a body",
            member.body.isEmpty) ++
      error(member, s"\"verified\" function \"${member.id.name}\" must have a decreases clause",
            member.spec.terminationMeasures.isEmpty) ++
      error(member, s"\"verified\" function \"${member.id.name}\" must have at least one result",
            member.result.outs.isEmpty) ++
      error(member, s"\"verified\" function \"${member.id.name}\" must have at least one ensures clause",
            member.spec.posts.isEmpty) ++
      member.spec.pres.flatMap(check(_, "precondition")) ++
      member.spec.posts.flatMap(check(_, "postcondition")) ++
      member.spec.preserves.flatMap(check(_, "preserves clause")) ++
      member.body.toVector.flatMap { case (_, block) =>
        allChildren(block).collect {
          case a: PAssume => error(a, s"\"verified\" function \"${member.id.name}\" must not contain assume")
          case a: PInhale => error(a, s"\"verified\" function \"${member.id.name}\" must not contain inhale")
        }.flatten
      }
    } else noMessages
  }

  private[typing] def wellDefIfVerifiedMethod(member: PMethodDecl): Messages = {
    if (member.spec.isVerified) {
      val isPtr = member.receiver.typ.isInstanceOf[PMethodReceivePointer]
      val check = validateVerifiedSpecClause("method", member.id.name, _: PNode, _: String, isPtr)
      error(member, s"\"verified\" method \"${member.id.name}\" must have a body",
            member.body.isEmpty) ++
      error(member, s"\"verified\" method \"${member.id.name}\" must have a decreases clause",
            member.spec.terminationMeasures.isEmpty) ++
      error(member, s"\"verified\" method \"${member.id.name}\" must have at least one result",
            member.result.outs.isEmpty) ++
      error(member, s"\"verified\" method \"${member.id.name}\" must have at least one ensures clause",
            member.spec.posts.isEmpty) ++
      member.spec.pres.flatMap(check(_, "precondition")) ++
      member.spec.posts.flatMap(check(_, "postcondition")) ++
      member.spec.preserves.flatMap(check(_, "preserves clause")) ++
      member.body.toVector.flatMap { case (_, block) =>
        allChildren(block).collect {
          case a: PAssume => error(a, s"\"verified\" method \"${member.id.name}\" must not contain assume")
          case a: PInhale => error(a, s"\"verified\" method \"${member.id.name}\" must not contain inhale")
        }.flatten
      }
    } else noMessages
  }

  private[typing] def wellDefIfVerifiedInterfaceMethod(sig: PMethodSig): Messages = {
    if (sig.spec.isVerified) {
      val check = validateVerifiedSpecClause("interface method", sig.id.name, _: PNode, _: String)
      error(sig, s"\"verified\" interface method \"${sig.id.name}\" must have a decreases clause",
            sig.spec.terminationMeasures.isEmpty) ++
      error(sig, s"\"verified\" interface method \"${sig.id.name}\" must have at least one result",
            sig.result.outs.isEmpty) ++
      error(sig, s"\"verified\" interface method \"${sig.id.name}\" must have at least one ensures clause",
            sig.spec.posts.isEmpty) ++
      sig.spec.pres.flatMap(check(_, "precondition")) ++
      sig.spec.posts.flatMap(check(_, "postcondition")) ++
      sig.spec.preserves.flatMap(check(_, "preserves clause"))
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
    else noMessages) ++
    {
      // Walk every pure/verified function/method called (directly or transitively) from this
      // spec clause and reject any whose own spec contains heap-permission or pointer-deref
      // nodes.  A single visited set prevents redundant re-traversal within one clause.
      val visited = scala.collection.mutable.Set[PNode]()
      (p +: allChildren(p)).collect { case invoke: PInvoke => invoke }.flatMap { invoke =>
        val resolved = resolve(invoke)
        // For interface methods called via implicit receiver, check the MethodSpec spec
        // directly since there is no body and pureCallTransitivelySafe only handles impls.
        val ifaceMethodMsgs: Messages = resolved match {
          case Some(ap.FunctionCall(ap.ImplicitlyReceivedInterfaceMethod(_, symb: MethodSpec), _))
              if symb.isPure || symb.isVerified =>
            val specNodes = (symb.spec.spec.pres ++ symb.spec.spec.posts ++ symb.spec.spec.preserves)
              .flatMap(q => q +: allChildren(q))
            if (specNodes.exists(n => n.isInstanceOf[PAccess] || n.isInstanceOf[PPredicateAccess]))
              error(p, s""""verified" $memberKind "$memberName" transitively calls interface method "${symb.spec.id.name}" which has acc() in its spec (in the $clauseKind)""")
            else if (specNodes.exists(_.isInstanceOf[PDeref]))
              error(p, s""""verified" $memberKind "$memberName" transitively calls interface method "${symb.spec.id.name}" which dereferences a pointer in its spec (in the $clauseKind)""")
            else noMessages
          case _ => noMessages
        }
        val calleeOpt: Option[Either[FuncSymbol, MethodImpl]] = resolved match {
          case Some(ap.FunctionCall(ap.Function(_, cs: FuncSymbol), _))               if cs.isPure || cs.isVerified => Some(Left(cs))
          case Some(ap.FunctionCall(ap.ReceivedMethod(_, _, _, cs: MethodImpl), _))   if cs.isPure || cs.isVerified => Some(Right(cs))
          case Some(ap.FunctionCall(ap.MethodExpr(_, _, _, cs: MethodImpl), _))       if cs.isPure || cs.isVerified => Some(Right(cs))
          case _ => None
        }
        ifaceMethodMsgs ++ calleeOpt
          .flatMap { cs =>
            val nextSpecOnly = cs.fold(_.isVerified, _.isVerified)
            pureCallTransitivelySafe(cs, visited, Vector.empty, nextSpecOnly)
          }
          .fold(noMessages: Messages) { chain =>
            error(p, s""""verified" $memberKind "$memberName" has a transitively heap-dependent call in its $clauseKind: $chain""")
          }
      }
    }
  }

  /** Walk the transitive closure of pure/verified function/method calls reachable from `symb`
    * and return a description of the first heap-dependent spec found, or None if clean.
    *
    * `specOnly` is true when following a verified callee: verified function bodies may freely
    * access the heap, so only the spec (pre/post/preserves) is checked, not the body.
    * `visited` prevents re-traversal of already-checked nodes (handles mutual recursion).
    * `chain` accumulates the call path for error messages. */
  private def pureCallTransitivelySafe(
      symb: Either[FuncSymbol, MethodImpl],
      visited: scala.collection.mutable.Set[PNode],
      chain: Vector[String],
      specOnly: Boolean
  ): Option[String] = {
    val (decl, spec, body, symbName) = symb match {
      case Left(f)  => (f.decl: PNode, f.decl.spec, f.decl.body, s"func ${f.decl.id.name}")
      case Right(m) => (m.decl: PNode, m.decl.spec, m.decl.body, s"method ${m.decl.id.name}")
    }
    if (visited.contains(decl)) return None
    visited.add(decl)

    val chainToHere = chain :+ symbName
    val specNodes = (spec.pres ++ spec.posts ++ spec.preserves).flatMap(p => p +: allChildren(p))
    val bodyNodes = if (specOnly) Vector.empty else body.toVector.flatMap { case (_, block) => block +: allChildren(block) }

    if (specNodes.exists(n => n.isInstanceOf[PAccess] || n.isInstanceOf[PPredicateAccess]))
      return Some(s"${chainToHere.mkString(" -> ")} has acc(...) in its spec")
    if (specNodes.exists(_.isInstanceOf[PDeref]))
      return Some(s"${chainToHere.mkString(" -> ")} dereferences a pointer in its spec")

    (specNodes ++ bodyNodes).iterator.collect { case invoke: PInvoke => invoke }.flatMap { invoke =>
      val calleeOpt: Option[(Either[FuncSymbol, MethodImpl], Boolean)] = resolve(invoke) match {
        case Some(ap.FunctionCall(ap.Function(_, cs: FuncSymbol), _))             if cs.isPure     => Some((Left(cs),  false))
        case Some(ap.FunctionCall(ap.ReceivedMethod(_, _, _, cs: MethodImpl), _)) if cs.isPure     => Some((Right(cs), false))
        case Some(ap.FunctionCall(ap.MethodExpr(_, _, _, cs: MethodImpl), _))     if cs.isPure     => Some((Right(cs), false))
        case Some(ap.FunctionCall(ap.Function(_, cs: FuncSymbol), _))             if cs.isVerified => Some((Left(cs),  true))
        case Some(ap.FunctionCall(ap.ReceivedMethod(_, _, _, cs: MethodImpl), _)) if cs.isVerified => Some((Right(cs), true))
        case Some(ap.FunctionCall(ap.MethodExpr(_, _, _, cs: MethodImpl), _))     if cs.isVerified => Some((Right(cs), true))
        case _ => None
      }
      calleeOpt.flatMap { case (cs, nextSpecOnly) => pureCallTransitivelySafe(cs, visited, chainToHere, nextSpecOnly) }
    }.nextOption()
  }

  private def containsHeapPermission(p: PNode): Boolean =
    (p +: allChildren(p)).exists(n => n.isInstanceOf[PAccess] || n.isInstanceOf[PPredicateAccess])

  private def containsPointerDereference(p: PNode): Boolean =
    (p +: allChildren(p)).exists(_.isInstanceOf[PDeref])

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
    val verifiedSigs: Vector[PMethodSig] =
      tree.root.programs.flatMap(p => allChildren(p))
        .collect { case ms: PMethodSig if ms.spec.isVerified => ms }
    val allVerified: Vector[PNode] = verifiedFuncs ++ verifiedMethods ++ verifiedSigs

    if (allVerified.isEmpty) return noMessages

    val verifiedFuncDecls: Set[PFunctionDecl] = verifiedFuncs.toSet
    val verifiedMethodDecls: Set[PMethodDecl]  = verifiedMethods.toSet
    val verifiedSigDecls: Set[PMethodSig]      = verifiedSigs.toSet

    // Display name used in error messages
    def nodeName(n: PNode): String = n match {
      case f: PFunctionDecl => s"func ${f.id.name}"
      case m: PMethodDecl   => s"method ${m.id.name}"
      case s: PMethodSig    => s"interface method ${s.id.name}"
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
          case Some(ap.FunctionCall(ap.ReceivedMethod(_, _, _, symb: MethodSpec), _))
              if symb.isVerified && verifiedSigDecls.contains(symb.spec) =>
            Some(symb.spec: PNode)
          case Some(ap.FunctionCall(ap.MethodExpr(_, _, _, symb: MethodSpec), _))
              if symb.isVerified && verifiedSigDecls.contains(symb.spec) =>
            Some(symb.spec: PNode)
          case Some(ap.FunctionCall(ap.ImplicitlyReceivedInterfaceMethod(_, symb: MethodSpec), _))
              if symb.isVerified && verifiedSigDecls.contains(symb.spec) =>
            Some(symb.spec: PNode)
          case _ => None
        }
      }.flatten.toSet

    // Collect edges from verified member body calls to other verified members.
    def calleesInBody(n: PNode): Set[PNode] = {
      val bodyNodes: Vector[PNode] = n match {
        case f: PFunctionDecl => f.body.toVector.flatMap { case (_, block) => allChildren(block) }
        case m: PMethodDecl   => m.body.toVector.flatMap { case (_, block) => allChildren(block) }
        case _                => Vector.empty  // PMethodSig has no body
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
        case s: PMethodSig =>
          (s.spec.pres ++ s.spec.posts ++ s.spec.preserves).flatMap(p => p +: allChildren(p))
        case _ => Vector.empty
      }
      resolveVerifiedCallees(specNodes)
    }

    val callGraph: Map[PNode, Set[PNode]] =
      allVerified.map(n => n -> (calleesInBody(n) ++ calleesInSpec(n))).toMap

    // Tarjan's SCC algorithm, implemented with immutable state passed through the recursion.
    case class TarjanState(
      idx:      Int             = 0,
      indexMap: Map[PNode, Int] = Map.empty,
      lowlink:  Map[PNode, Int] = Map.empty,
      onStack:  Set[PNode]      = Set.empty,
      stack:    Vector[PNode]   = Vector.empty,
      msgs:     Messages        = noMessages
    )

    def strongconnect(v: PNode, s: TarjanState): TarjanState = {
      val s1 = s.copy(
        idx      = s.idx + 1,
        indexMap = s.indexMap + (v -> s.idx),
        lowlink  = s.lowlink  + (v -> s.idx),
        onStack  = s.onStack  + v,
        stack    = s.stack    :+ v
      )
      val s2 = callGraph.getOrElse(v, Set.empty).foldLeft(s1) { (acc, w) =>
        if (!acc.indexMap.contains(w)) {
          val acc2 = strongconnect(w, acc)
          acc2.copy(lowlink = acc2.lowlink + (v -> math.min(acc2.lowlink(v), acc2.lowlink(w))))
        } else if (acc.onStack.contains(w)) {
          acc.copy(lowlink = acc.lowlink + (v -> math.min(acc.lowlink(v), acc.indexMap(w))))
        } else acc
      }
      if (s2.lowlink(v) == s2.indexMap(v)) {
        // Pop the SCC. Uses reference identity (`eq`) to identify the root, matching the
        // original algorithm's requirement that the stack is a true call stack.
        @scala.annotation.tailrec
        def popScc(stk: Vector[PNode], os: Set[PNode], acc: Vector[PNode]): (Vector[PNode], Vector[PNode], Set[PNode]) = {
          val w = stk.last
          val acc2 = acc :+ w
          if (w eq v) (acc2, stk.init, os - w)
          else         popScc(stk.init, os - w, acc2)
        }
        val (scc, stackTail, onStackRem) = popScc(s2.stack, s2.onStack, Vector.empty)
        val s3 = s2.copy(stack = stackTail, onStack = onStackRem)
        val cycleMsgs: Messages =
          if (scc.size > 1 || (scc.size == 1 && callGraph.getOrElse(v, Set.empty).contains(v))) {
            if (scc.size == 1)
              error(scc.head, s"\"verified\" ${nodeName(scc.head)} calls itself. Recursive domain axioms are unsound.")
            else {
              // Tarjan pops in reverse discovery order; reversing restores call order.
              val ordered   = scc.reverse
              val cycleDesc = s"${ordered.map(nodeName).mkString(" -> ")} -> ${nodeName(ordered.head)}"
              ordered.flatMap(n => error(n, s"\"verified\" members form a recursion cycle: $cycleDesc. Recursive domain axioms are unsound."))
            }
          } else noMessages
        s3.copy(msgs = s3.msgs ++ cycleMsgs)
      } else s2
    }

    allVerified.foldLeft(TarjanState()) { (s, n) =>
      if (s.indexMap.contains(n)) s else strongconnect(n, s)
    }.msgs
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
    (if (mSpec.spec.spec.terminationMeasures.nonEmpty && mImpl.decl.spec.terminationMeasures.isEmpty)
      error(mImpl.decl.spec, s"This method tries to implement a terminating interface method, " +
        s"but it does not provide a termination measure.")
    else
      noMessages) ++
    (if (mSpec.isVerified && !mImpl.isVerified)
      error(mImpl.decl.spec,
        s"""method "${mImpl.decl.id.name}" implements verified interface method "${mSpec.spec.id.name}" but is not marked "verified" (must also include a "decreases" clause)""")
    else
      noMessages)
  }
}

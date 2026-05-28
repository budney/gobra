// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.translator.encodings.defaults

import org.bitbucket.inkytonik.kiama.==>
import viper.gobra.ast.{internal => in}
import viper.gobra.translator.encodings.combinators.Encoding
import viper.gobra.translator.context.Context
import viper.gobra.translator.util.{VprInfo, ViperUtil => vu}
import viper.silver.ast.Method
import viper.silver.{ast => vpr}

class DefaultMethodEncoding extends Encoding {

  import viper.gobra.translator.util.ViperWriter.{CodeLevel => cl, _}
  import MemberLevel._

  override def member(ctx: Context): in.Member ==> MemberWriter[Vector[vpr.Member]] = {
    val parent = super.member(ctx)
    val verifiedCase: in.Member ==> MemberWriter[Vector[vpr.Member]] = {
      case x: in.Function if x.isVerified => verifiedFunctionMembers(x)(ctx)
      case x: in.Method   if x.isVerified => verifiedMethodMembers(x)(ctx)
    }
    verifiedCase.orElse(parent)
  }

  override def method(ctx: Context): in.Member ==> MemberWriter[vpr.Method] = {
    case x: in.Method => methodDefault(x)(ctx)
    case x: in.Function => functionDefault(x)(ctx)
  }

  def methodDefault(x: in.Method)(ctx: Context): MemberWriter[vpr.Method] = {
    val (pos, info, errT) = x.vprMeta

    val vRecv = ctx.variable(x.receiver)
    val vRecvPres = ctx.varPrecondition(x.receiver).toVector

    val vArgs = x.args.map(ctx.variable)
    val vArgPres = x.args.flatMap(ctx.varPrecondition)

    val vResults = x.results.map(ctx.variable)
    val vResultPosts = x.results.flatMap(ctx.varPostcondition)
    val vResultInit = cl.seqns(x.results map ctx.initialization)

    for {
      pres <- sequence((vRecvPres ++ vArgPres) ++ x.pres.map(ctx.precondition))
      posts <- sequence(vResultPosts ++ x.posts.map(ctx.postcondition))
      measures <- sequence(x.terminationMeasures.map(e => pure(ctx.assertion(e))(ctx)))

      body <- option(x.body.map{ b => block{
        for {
          init <- vResultInit
          core <- ctx.statement(b)
        } yield vu.seqn(Vector(init, core))(pos, info, errT)
      }})

      annotatedInfo = VprInfo.attachAnnotations(x.backendAnnotations, info)

      method = vpr.Method(
        name = x.name.uniqueName,
        formalArgs = vRecv +: vArgs,
        formalReturns = vResults,
        pres = pres ++ measures,
        posts = posts,
        body = body
      )(pos, annotatedInfo, errT)

    } yield method
  }


  def functionDefault(x: in.Function)(ctx: Context): MemberWriter[Method] = {
    assert(x.info.origin.isDefined, s"$x has no defined source")

    val (pos, info, errT) = x.vprMeta

    val vArgs = x.args.map(ctx.variable)
    val vArgPres = x.args.flatMap(ctx.varPrecondition)

    val vResults = x.results.map(ctx.variable)
    val vResultPosts = x.results.flatMap(ctx.varPostcondition)
    val vResultInit = cl.seqns(x.results map ctx.initialization)

    for {
      pres <- sequence(vArgPres ++ x.pres.map(ctx.precondition))
      posts <- sequence(vResultPosts ++ x.posts.map(ctx.postcondition))
      measures <- sequence(x.terminationMeasures.map(e => pure(ctx.assertion(e))(ctx)))

      body <- option(x.body.map{ b => block{
        for {
          init <- vResultInit
          core <- ctx.statement(b)
        } yield vu.seqn(Vector(init, core))(pos, info, errT)
      }})

      annotatedInfo = VprInfo.attachAnnotations(x.backendAnnotations, info)

      method = vpr.Method(
        name = x.name.name,
        formalArgs = vArgs,
        formalReturns = vResults,
        pres = pres ++ measures,
        posts = posts,
        body = body
      )(pos, annotatedInfo, errT)

    } yield method
  }

  private def verifiedMethodMembers(x: in.Method)(ctx: Context): MemberWriter[Vector[vpr.Member]] = {
    val (pos, info, errT) = x.vprMeta
    val vRecv    = ctx.variable(x.receiver)
    val vArgs    = x.args.map(ctx.variable)
    val vResults = x.results.map(ctx.variable)
    for {
      method <- methodDefault(x)(ctx)
      vPres  <- sequence(x.pres.map(ctx.precondition))
      vPosts <- sequence(x.posts.map(ctx.postcondition))
      domain  = buildVerifiedDomain(x.name.uniqueName, vRecv +: vArgs, vResults, vPres, vPosts)(pos, info, errT)(ctx)
    } yield Vector(method, domain)
  }

  private def verifiedFunctionMembers(x: in.Function)(ctx: Context): MemberWriter[Vector[vpr.Member]] = {
    val (pos, info, errT) = x.vprMeta
    val vArgs    = x.args.map(ctx.variable)
    val vResults = x.results.map(ctx.variable)
    for {
      method <- functionDefault(x)(ctx)
      vPres  <- sequence(x.pres.map(ctx.precondition))
      vPosts <- sequence(x.posts.map(ctx.postcondition))
      domain  = buildVerifiedDomain(x.name.name, vArgs, vResults, vPres, vPosts)(pos, info, errT)(ctx)
    } yield Vector(method, domain)
  }

  // Builds the Viper domain that backs a verified function or method:
  //   - a spec domain function {memberName}_spec(allArgs) : retType
  //   - one anonymous axiom per postcondition: ∀ allArgs :: pre → post[result ↦ spec(allArgs)]
  // allArgs includes the receiver for methods and is empty-safe (bare axiom for zero-arg functions).
  private def buildVerifiedDomain(
      memberName: String,
      allArgs:    Vector[vpr.LocalVarDecl],
      vResults:   Vector[vpr.LocalVarDecl],
      vPres:      Vector[vpr.Exp],
      vPosts:     Vector[vpr.Exp]
  )(pos: vpr.Position, info: vpr.Info, errT: vpr.ErrorTrafo)(ctx: Context): vpr.Domain = {
    val domainName = memberName + "_domain"
    val retType: vpr.Type = vResults.size match {
      case 1 => vResults.head.typ
      case _ => ctx.tuple.typ(vResults.map(_.typ))
    }
    val specFunc = vpr.DomainFunc(
      name       = memberName + "_spec",
      formalArgs = allArgs,
      typ        = retType
    )(pos, info, domainName, errT)
    val specApp = vpr.DomainFuncApp(
      funcname  = memberName + "_spec",
      args      = allArgs.map(_.localVar),
      typVarMap = Map.empty
    )(pos, info, retType, domainName, errT)
    val resultSubst: Map[vpr.LocalVar, vpr.Exp] = vResults.size match {
      case 1 => Map(vResults.head.localVar -> specApp)
      case n => vResults.zipWithIndex.map { case (rv, i) =>
        rv.localVar -> ctx.tuple.get(specApp, i, n)(pos, info, errT)
      }.toMap
    }
    val axioms = vPosts.map { post =>
      val substituted = post.replace(resultSubst)
      val body = if (vPres.nonEmpty) vpr.Implies(vu.bigAnd(vPres)(pos, info, errT), substituted)(pos, info, errT) else substituted
      // vpr.Forall requires ≥1 quantified variable; for zero-arg functions emit a bare axiom.
      val axiomExp: vpr.Exp = if (allArgs.isEmpty) body
        else vpr.Forall(allArgs, Seq(vpr.Trigger(Seq(specApp))(pos, info, errT)), body)(pos, info, errT)
      vpr.AnonymousDomainAxiom(axiomExp)(pos, info, domainName, errT): vpr.DomainAxiom
    }
    vpr.Domain(
      name      = domainName,
      functions = Seq(specFunc),
      axioms    = axioms
    )(pos, info, errT)
  }
}

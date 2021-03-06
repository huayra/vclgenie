package com.iheart.controllers

import com.iheart.json.Formats._
import com.iheart.models._
import play.Logger
import play.api.libs.json.{JsSuccess, Json, JsError}
import play.api.mvc.Results._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


trait BaseController {

  def getErrors[S <: BaseError,T](coll: Seq[Either[S,T]]): Seq[String] = coll.filter(c => c.isLeft).flatMap(c => c.left.get.errors)

  implicit class errorJClass(jserror: JsError) {

    def errorJF  = Future {
      val msgs = jserror.errors.flatMap { e =>
        e._2.map { v  =>
          v.message + " : " + e._1.toString()
        }
      }
      Logger.info("Error: " + jserror)
      BadRequest(Json.toJson(RuleError(msgs)))
    }
  }

  implicit class errorRuleClass(ruleError: RuleError) {

    def errorJF = Future {
      BadRequest(Json.toJson(ruleError))
    }
  }

  implicit class successOkClass(str: String) {
    def successF = Future {
      Ok(str)
    }
  }

  implicit class validRulesClass(seq: JsSuccess[Seq[Either[RuleError,Rule]]]) {
    def isValid: Boolean =
      seq.getOrElse(Seq()).count(s => s.isLeft) == 0

    def errorJF = Future {
      BadRequest(Json.toJson(seq.getOrElse(Seq()).filter(s => s.isLeft).map(r => r.left.get)))
    }
  }

  implicit class validVclRequestClass(req: JsSuccess[Either[RequestError,VclRequest]]) {

    lazy val request: VclRequest = req.get.right.get
    lazy val requestErr: RequestError = req.get.left.get

    def isValid: Boolean =
      req.get.isRight match {
        case false =>  false
        case true =>
            //See if any of the params are an XError (Left)
            request.orderedRules.count(s => s.isLeft) == 0 &&
            request.hostnames.count(h => h.isLeft) == 0 &&
            request.globalRules.count(s => s.isLeft) == 0 &&
            request.backends.count(b => b.isLeft) == 0
      }

    def ruleErrors: Seq[RuleError] = req.get.isRight match {
      case true => Seq(RuleError(getErrors(request.orderedRules)), RuleError(getErrors(request.globalRules)))
      case false => Seq()
    }

    def hostnameErrors: Seq[HostnameError] = req.get.isRight match {
      case true => request.hostnames.filter(h => h.isLeft).map(r => r.left.get)
      case false => Seq()
    }

    def backendErrors: Seq[BackendError] = req.get.isRight match {
      case true => request.backends.filter(b => b.isLeft).map(b => b.left.get)
      case false => Seq()
    }

    def requestErrors: Seq[RequestError] = req.get.isLeft match {
      case true => Seq(req.get.left.get)
      case false => Seq()
    }

    def toOrderedRules: Seq[Rule] =
      req.get.isRight match {
        case false => Seq()
        case true => request.orderedRules.filter(res => res.isRight).map(res => res.right.get)
      }

    def toGlobalRules =
      req.get.isRight match {
        case false => Seq()
        case true => request.globalRules.filter(res => res.isRight).map(res => res.right.get)
      }

    def toHostnames =
      req.get.isRight match {
        case false => Seq()
        case true => request.hostnames.filter(res => res.isRight).map(res => res.right.get)
      }

    def toBackends =
      req.get.isRight match {
        case false => Seq()
        case true => request.backends.filter(res => res.isRight).map(res => res.right.get)
      }


    def errorToString[T <: BaseError](c: Seq[T]): Seq[String] = c.flatMap(e => e.errors)

    def errorJF = Future {
      val errors: Seq[String] = errorToString(ruleErrors) ++ errorToString(hostnameErrors) ++ errorToString(backendErrors) ++ errorToString(requestErrors)
      Logger.info("returning errors : " + errors)
      BadRequest(Json.obj("errors" -> errors))
    }
  }

}

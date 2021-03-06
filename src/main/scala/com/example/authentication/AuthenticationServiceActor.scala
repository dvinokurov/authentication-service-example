package com.example.authentication

import akka.actor.{Actor, ActorRefFactory, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import spray.can.Http
import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.routing.{HttpService, Route}

import scala.concurrent.duration._

case class AuthenticationSuccess(token: String)
case class AuthenticationFailure(error: String)

object JsonImplicits extends DefaultJsonProtocol{
  implicit val authenticationSuccessFormat = jsonFormat1(AuthenticationSuccess)
  implicit val authenticationFailureFormat = jsonFormat1(AuthenticationFailure)
}

class AuthenticationServiceActor extends Actor with HttpService with SprayJsonSupport{
  import JsonImplicits._

  def receive: Receive = runRoute(
    respondWithMediaType(`application/json`) {
      path("token" / "refresh"){
        get{
          parameters('token){token: String=>
            if(token=="716348726348"){
              respondWithStatus(StatusCodes.OK){
                complete{
                  AuthenticationSuccess("1")
                }
              }
            }else{
              respondWithStatus(StatusCodes.Unauthorized){
                complete{
                  AuthenticationFailure("not authorized")
                }
              }
            }
          }
        }
      } ~
      path("token") {
        get {
          parameters('userName, 'userPassword) { (userName, userPassword) =>
              checkAuthentication(userName, userPassword)
          }
        }
      } ~
        path("check") {
          get {
            parameters('token) { token =>
              checkToken(token)
            }
          }
        }
    }
    )

  def checkAuthentication(userName: String, userPassword: String): Route = {
    if(userName == CredentialsHolder.userName && userPassword==CredentialsHolder.userPassword) {
      respondWithStatus(StatusCodes.OK) {
       complete{
         AuthenticationSuccess("716348726348")
        }
      }
    } else {
      respondWithStatus(StatusCodes.Unauthorized) {
        complete {
          AuthenticationFailure("not authorized")
        }
      }
    }
  }

  def checkToken(token:String):Route = {
    if(List("716348726348", "1").contains(token))   {
      respondWithStatus(200) {
        complete{
          AuthenticationSuccess(token)
        }
      }
    } else if(token=="12"){
      respondWithStatus(401) {
        complete {
          AuthenticationFailure("token expired")
        }
      }
    } else {
      respondWithStatus(401) {
        complete {
          AuthenticationFailure("not authorized")
        }
      }
    }
  }

  def actorRefFactory: ActorRefFactory = context
}

object CredentialsHolder{
  private val config: Config = ConfigFactory.load()
  val userName = config.getString("user.login")
  val userPassword = config.getString("user.password")
}

object AuthenticationServiceRunner extends App{
  implicit val system = ActorSystem("authentication-actor-system")
  implicit val timeout = Timeout(5.seconds)
  
  val service = system.actorOf(Props[AuthenticationServiceActor], "authentication-service")

  IO(Http) ? Http.Bind(service, interface = "localhost", port=8020)
}
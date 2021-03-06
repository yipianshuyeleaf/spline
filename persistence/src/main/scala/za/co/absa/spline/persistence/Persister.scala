/*
 * Copyright 2019 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.spline.persistence

import java.util.concurrent.CompletionException

import com.arangodb.ArangoDBException
import org.slf4s.Logging

import scala.concurrent.Future

object Persister extends Logging {

  import scala.concurrent.ExecutionContext.Implicits._

  private[persistence] val MaxRetries = 5

  def execute[R](fn: => Future[R]): Future[R] = {
    executeWithRetry(fn, None)
  }

  @throws(classOf[IllegalArgumentException])
  @throws(classOf[ArangoDBException])
  @throws(classOf[CompletionException])
  private def executeWithRetry[R](fn: => Future[R], lastFailure: Option[FailedAttempt]): Future[R] = {
    val eventualResult = fn
    val attemptsUsed = lastFailure.map(_.count).getOrElse(0)

    for (failure <- lastFailure) eventualResult.foreach { _ =>
      log.warn(s"Succeeded after ${failure.count + 1} attempts. Previous message was: ${failure.error.getMessage}")
    }

    if (attemptsUsed >= MaxRetries)
      eventualResult
    else
      eventualResult.recoverWith {
        case RetryableException(e) => executeWithRetry(fn, Some(FailedAttempt(attemptsUsed + 1, e)))
      }
  }

  case class FailedAttempt(count: Int, error: Exception)

}

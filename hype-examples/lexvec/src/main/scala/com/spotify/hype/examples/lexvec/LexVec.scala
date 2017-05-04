package com.spotify.hype.examples.lexvec

import com.spotify.hype.examples.{CmdLineEmbeddingModel, HypeModule}


case class LexVec(train: String,
                  output: String,
                  cv: String,
                  args: (String, String)*) extends HypeModule[Seq[(String, String)]] with CmdLineEmbeddingModel {

  override def cmd(train: String, output: String): String = {
    val formatedArgs: String = args.map { case (k, v) => s"-$k $v" }.mkString(" ")
    s"lexvec -corpus $train -output $output $formatedArgs"
  }

  override def getImage: String = "us.gcr.io/datawhere-test/hype-lexvec:1"

  override def getFn: Seq[(String, String)] = {
    run(train, output, cv)
  }

}

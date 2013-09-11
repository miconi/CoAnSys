/*
 * This file is part of CoAnSys project.
 * Copyright (c) 2012-2013 ICM-UW
 * 
 * CoAnSys is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * CoAnSys is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with CoAnSys. If not, see <http://www.gnu.org/licenses/>.
 */

package pl.edu.icm.coansys.citations.tools.sequencefile

import resource._
import org.apache.hadoop.conf.Configuration
import pl.edu.icm.coansys.citations.util.sequencefile.SequenceFileIterator

/**
 * @author Mateusz Fedoryszak (m.fedoryszak@icm.edu.pl)
 */
object Wc {
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Counts records in a given sequence file.")
      println()
      println("Usage: Wc <sequence_file>")
      System.exit(1)
    }

    val inUri = args(0)
    val count = managed(SequenceFileIterator.fromUri(new Configuration(), inUri)).acquireAndGet(_.size)
    println(count + " records")
  }
}

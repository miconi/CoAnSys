--
-- This file is part of CoAnSys project.
-- Copyright (c) 2012-2013 ICM-UW
-- 
-- CoAnSys is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.

-- CoAnSys is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-- GNU Affero General Public License for more details.
-- 
-- You should have received a copy of the GNU Affero General Public License
-- along with CoAnSys. If not, see <http://www.gnu.org/licenses/>.
--
-- -----------------------------------------------------
-- -----------------------------------------------------
-- macros section
-- -----------------------------------------------------
-- -----------------------------------------------------
DEFINE measureCosineDistane(CroZ) RETURNS J{
	I = foreach $CroZ generate 
		flatten(pl.edu.icm.coansys.classification.
		documents.pig.proceeders.DOCSIM(*)) 
		as (keyA:chararray, keyB:chararray, sim:double);
	$J = filter I by keyA is not null;
--	$K = group J by keyA;
};

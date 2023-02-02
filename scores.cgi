#!/pub/bin/perl -w

use strict;

print <<END;
Content-type: text/html

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0//EN"
	"http://www.w3.org/TR/REC-html40/strict.dtd">
<html>
	<head>
		<title>Pop A Prof Scores</title>
		<link rel="stylesheet" href="cs.css">
		<meta http-equiv="refresh" content=10;>
	</head>
	<body>
		<h1>Current Players and Scores</h1>

		<table>
END
open(FILE,"<scores");
while (defined($_=<FILE>)) {
	chomp;
	my ($name,$score)=split(/ /);
	print <<END;
			<tr>
				<td>
					<font class="pretty">$name</font>
				</td>
				<td>
					<font class="pretty">$score</font>
				</td>
			</tr>
END
}
print <<END;
		</table>
		<hr>
		<p>
			<a href="index.html">
				Back to Pop A Prof</a>
		</p>
	</body>
</html>
END

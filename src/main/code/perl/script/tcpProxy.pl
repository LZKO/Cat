#!/user/bin/perlnew
use strict;
use warnings;
use File::Basename;
my $name = File::Basename::basename($0);
use Ner::Proxy;
use Getopt::Long;


my $sid;
my $localport;
my $ifDeamon = 0;

my $dbinfo = <<_DB_;
    ad = ****.local:389
    devmail = devsmtprelay.****.com.cn:25
    test = 10.31.224.226:6613

_DB_
my $usage = <<_USAGE_;
    usage: $name -s sid
    or $name -s sid -p localport
    or $name -s sid -p localport -d 1
当前记录:
$dbinfo
_USAGE_


GetOptions ("p=i" => \$localport,
              "s=s" => \$sid,
              "d=i" => \$ifDeamon,
              "h"   => sub {print $usage;exit}  );#flag
if ( not defined $sid ) {
    print usage;
    exit;
}


my $db = map {
              s/\s//g;
              my ($sid, $remotehost, $remoteport) = split(/\s*[=:]\s*/, $_, 3);
              $sid => { 'remote' => $remotehost, 'remoteport' => $remoteport}
              } grep { ! /^\s*$/ } split(/\n/, $dbinfo);


if ( not exists $db{$sid} ) {
    print("ERROR: $sid 88888 \n");
    exit(1);
}


if ( not defined $localport ) {
    $localport = $db{$sid}{'remoteport'};
}
my ($remotehost, $remoteport) = ($db{$sid}{'remotehost'}, $db{$sid}{'remoteport'});


print("start 0.0.0.0:$localport <=> $remotehost:$remoteport\n");

my $pid = 0;
if ( $ifDeamon == 1) {
    $pid = fork();
}

if( $pid ) {
    exit;
} else {
    my $proxy = Net::Proxy->new({
        in => { type => "tcp", host => "0.0.0.0", port => $localport },
        out => { type => "tcp", host => $remotehost, port => $remoteport },
    });
    $proxy->register();

    Net::Proxy->mainloop();
}

